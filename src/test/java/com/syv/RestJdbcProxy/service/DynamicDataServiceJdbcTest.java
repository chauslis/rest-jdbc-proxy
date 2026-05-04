package com.syv.RestJdbcProxy.service;

import com.syv.RestJdbcProxy.config.DynamicDataSourceContextHolder;
import com.syv.RestJdbcProxy.init.AliasConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicDataServiceJdbcTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DynamicDataService dynamicDataService = new DynamicDataService();

    @AfterEach
    void tearDown() {
        DynamicDataSourceContextHolder.clearDataSourceKey();
    }

    @Test
    void executeDynamicQueryWithParamsSetsConnectionAndAddsItToRows() {
        ReflectionTestUtils.setField(dynamicDataService, "jdbcTemplate", jdbcTemplate);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("connection", "DB2");
        parameters.put("id", 7);
        when(jdbcTemplate.queryForList(eq("select * from customer where id = ?"), any(Object[].class)))
                .thenReturn(List.of(new HashMap<>(Map.of("ID", 7))));

        List<Map<String, Object>> result = dynamicDataService.executeDynamicQuery(
                "select * from customer where id = ?",
                parameters
        );

        assertEquals(null, DynamicDataSourceContextHolder.getDataSourceKey());
        assertEquals(List.of(Map.of("ID", 7, "connection", "DB2")), result);
        assertEquals(Map.of("id", 7), parameters);
        verify(jdbcTemplate).queryForList(eq("select * from customer where id = ?"), any(Object[].class));
    }

    @Test
    void executeDynamicQueryWithParamsClearsConnectionWhenJdbcCallFails() {
        ReflectionTestUtils.setField(dynamicDataService, "jdbcTemplate", jdbcTemplate);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("connection", "DB2");
        parameters.put("id", 7);
        when(jdbcTemplate.queryForList(eq("select * from customer where id = ?"), any(Object[].class)))
                .thenThrow(new RuntimeException("query failed"));

        assertThrows(RuntimeException.class, () -> dynamicDataService.executeDynamicQuery(
                "select * from customer where id = ?",
                parameters
        ));

        assertEquals(null, DynamicDataSourceContextHolder.getDataSourceKey());
    }

    @Test
    void distributeAndExecuteQuerySupportsMixedDatabaseConnectionsInOneRequest() throws Exception {
        ReflectionTestUtils.setField(dynamicDataService, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(dynamicDataService, "executorService", new SameThreadExecutorService());
        List<Map<String, Object>> parameters = List.of(
                new HashMap<>(Map.of("connection", "DB1", "id", 1)),
                new HashMap<>(Map.of("connection", "DB2", "id", 2)),
                new HashMap<>(Map.of("connection", "DB1", "id", 3)),
                new HashMap<>(Map.of("connection", "DB3", "id", 4))
        );
        when(jdbcTemplate.queryForList(eq("select * from customer where id = ?"), any(Object[].class)))
                .thenAnswer(invocation -> {
                    Object firstParameter = invocation.getArgument(1);
                    return List.of(new HashMap<>(Map.of("ID", firstParameter)));
                });

        List<List<Map<String, Object>>> result = dynamicDataService.distributeAndExecuteQuery(
                "select * from customer where id = ?",
                parameters,
                2
        );

        assertEquals(List.of(
                List.of(Map.of("ID", 1, "connection", "DB1")),
                List.of(Map.of("ID", 2, "connection", "DB2")),
                List.of(Map.of("ID", 3, "connection", "DB1")),
                List.of(Map.of("ID", 4, "connection", "DB3"))
        ), result);
        verify(jdbcTemplate, times(4)).queryForList(eq("select * from customer where id = ?"), any(Object[].class));
    }

    @Test
    void convertsSupportedJdbcTypes() {
        assertEquals(Types.VARCHAR, DynamicDataService.convertStringToJdbcType("varchar"));
        assertEquals(Types.INTEGER, DynamicDataService.convertStringToJdbcType("INTEGER"));
        assertEquals(Types.BIGINT, DynamicDataService.convertStringToJdbcType("BIGINT"));
        assertEquals(Types.DOUBLE, DynamicDataService.convertStringToJdbcType("DOUBLE"));
        assertEquals(Types.REF_CURSOR, DynamicDataService.convertStringToJdbcType("SYS_REFCURSOR"));
    }

    @Test
    void rejectsUnsupportedJdbcType() {
        assertThrows(IllegalArgumentException.class, () -> DynamicDataService.convertStringToJdbcType("ARRAY"));
    }

    @Test
    void executeAliasBatchMissingAliasReturnsNotFound() {
        dynamicDataService.aliasConfigMap = Map.of();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> dynamicDataService.executeAliasBatch("missing", List.of(Map.of("connection", "DB1")))
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getResponseFromSpAllowsMissingOutParamDescriptor() {
        DynamicDataService service = spy(new DynamicDataService());
        AliasConfig aliasConfig = callableAliasWithoutOutParams();
        Map<String, Object> parameters = new HashMap<>(Map.of("connection", "DB1", "ID", 1));
        doReturn(Map.of("status", "ok"))
                .when(service)
                .executeStoreFuncWithDynamicParams(eq("test_pkg"), eq("test_proc"), anyMap(), same(parameters), eq(Map.of()));

        ResponseEntity<List<Map<String, Object>>> response = service.getResponseFromSP(parameters, aliasConfig);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(Map.of("status", "ok")), response.getBody());
        verify(service).executeStoreFuncWithDynamicParams(eq("test_pkg"), eq("test_proc"), anyMap(), same(parameters), eq(Map.of()));
    }

    private static AliasConfig callableAliasWithoutOutParams() {
        AliasConfig config = new AliasConfig();
        AliasConfig.Alias alias = new AliasConfig.Alias();
        AliasConfig.CallableStatements callableStatements = new AliasConfig.CallableStatements();
        callableStatements.setDbSpName("test_pkg.test_proc");
        AliasConfig.InParam inParam = new AliasConfig.InParam();
        AliasConfig.Param idParam = new AliasConfig.Param();
        idParam.setJdbcParamName("ID");
        idParam.setJdbcParamType("INTEGER");
        inParam.setParam(List.of(idParam));
        callableStatements.setInParam(inParam);
        alias.setCallableStatements(callableStatements);
        config.setAlias(alias);
        return config;
    }
}
