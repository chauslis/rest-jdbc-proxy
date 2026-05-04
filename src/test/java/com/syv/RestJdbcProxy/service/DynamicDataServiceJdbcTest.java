package com.syv.RestJdbcProxy.service;

import com.syv.RestJdbcProxy.config.DynamicDataSourceContextHolder;
import com.syv.RestJdbcProxy.dto.GatewayMetadata;
import com.syv.RestJdbcProxy.dto.GatewayRequest;
import com.syv.RestJdbcProxy.init.OperationConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void executeDynamicQueryUsesEnvelopeConnectionAndDoesNotMutateBusinessConnectionParam() {
        ReflectionTestUtils.setField(dynamicDataService, "jdbcTemplate", jdbcTemplate);
        Map<String, Object> params = new HashMap<>();
        params.put("connection", "business-value");
        params.put("id", 7);
        GatewayRequest request = gatewayRequest("DB2", params);
        List<OperationConfig.ParameterDescriptor> inputParameters = List.of(param("id", "BIGINT", 1));
        when(jdbcTemplate.queryForList(eq("select * from customer where id = ?"), any(Object[].class)))
                .thenReturn(List.of(new HashMap<>(Map.of("ID", 7))));

        List<Map<String, Object>> result = dynamicDataService.executeDynamicQuery(
                "select * from customer where id = ?",
                request,
                inputParameters
        );

        assertEquals(null, DynamicDataSourceContextHolder.getDataSourceKey());
        assertEquals(List.of(Map.of("ID", 7, "_rjp_connectionName", "DB2")), result);
        assertEquals(Map.of("connection", "business-value", "id", 7), params);
        verify(jdbcTemplate).queryForList(eq("select * from customer where id = ?"), any(Object[].class));
    }

    @Test
    void executeDynamicQueryClearsConnectionWhenJdbcCallFails() {
        ReflectionTestUtils.setField(dynamicDataService, "jdbcTemplate", jdbcTemplate);
        GatewayRequest request = gatewayRequest("DB2", Map.of("id", 7));
        when(jdbcTemplate.queryForList(eq("select * from customer where id = ?"), any(Object[].class)))
                .thenThrow(new RuntimeException("query failed"));

        assertThrows(RuntimeException.class, () -> dynamicDataService.executeDynamicQuery(
                "select * from customer where id = ?",
                request,
                List.of(param("id", "BIGINT", 1))
        ));

        assertEquals(null, DynamicDataSourceContextHolder.getDataSourceKey());
    }

    @Test
    void distributeAndExecuteQuerySupportsMixedDatabaseConnectionsInOneRequest() throws Exception {
        ReflectionTestUtils.setField(dynamicDataService, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(dynamicDataService, "executorService", new SameThreadExecutorService());
        List<GatewayRequest> requests = List.of(
                gatewayRequest("DB1", Map.of("id", 1)),
                gatewayRequest("DB2", Map.of("id", 2)),
                gatewayRequest("DB1", Map.of("id", 3)),
                gatewayRequest("DB3", Map.of("id", 4))
        );
        when(jdbcTemplate.queryForList(eq("select * from customer where id = ?"), any(Object[].class)))
                .thenAnswer(invocation -> {
                    Object firstParameter = invocation.getArgument(1);
                    return List.of(new HashMap<>(Map.of("ID", firstParameter)));
                });

        List<List<Map<String, Object>>> result = dynamicDataService.distributeAndExecuteQuery(
                "select * from customer where id = ?",
                requests,
                List.of(param("id", "BIGINT", 1)),
                2
        );

        assertEquals(List.of(
                List.of(Map.of("ID", 1, "_rjp_connectionName", "DB1")),
                List.of(Map.of("ID", 2, "_rjp_connectionName", "DB2")),
                List.of(Map.of("ID", 3, "_rjp_connectionName", "DB1")),
                List.of(Map.of("ID", 4, "_rjp_connectionName", "DB3"))
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
        dynamicDataService.operationConfigMap = Map.of();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> dynamicDataService.executeAliasBatch("missing", List.of(gatewayRequest("DB1", Map.of("ID", 1))))
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void invalidRequestMissingMetadataReturnsBadRequest() {
        GatewayRequest request = new GatewayRequest();
        request.setParams(Map.of("ID", 1));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> dynamicDataService.executeAliasBatch("missing", List.of(request))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void invalidRequestMissingParamsReturnsBadRequest() {
        GatewayRequest request = new GatewayRequest();
        GatewayMetadata metadata = new GatewayMetadata();
        metadata.setConnectionName("DB1");
        request.setRjp(metadata);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> dynamicDataService.executeAliasBatch("missing", List.of(request))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void getResponseFromSpAllowsMissingOutParamDescriptor() {
        DynamicDataService service = spy(new DynamicDataService());
        OperationConfig operationConfig = callableOperationWithoutOutParams();
        GatewayRequest request = gatewayRequest("DB1", Map.of("ID", 1));
        doReturn(Map.of("status", "ok"))
                .when(service)
                .executeStoreFuncWithDynamicParams(
                        eq("test_pkg"),
                        eq("test_proc"),
                        eq(operationConfig.getOperationDescriptor().getInputParameters()),
                        eq(request),
                        eq(List.of())
                );

        ResponseEntity<List<Map<String, Object>>> response = service.getResponseFromSP(request, operationConfig);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(Map.of("status", "ok")), response.getBody());
    }

    private static GatewayRequest gatewayRequest(String connectionName, Map<String, Object> params) {
        GatewayMetadata metadata = new GatewayMetadata();
        metadata.setConnectionName(connectionName);
        GatewayRequest request = new GatewayRequest();
        request.setRjp(metadata);
        request.setParams(params);
        return request;
    }

    private static OperationConfig.ParameterDescriptor param(String name, String jdbcType, int position) {
        OperationConfig.ParameterDescriptor descriptor = new OperationConfig.ParameterDescriptor();
        descriptor.setName(name);
        descriptor.setJdbcType(jdbcType);
        descriptor.setPosition(position);
        return descriptor;
    }

    private static OperationConfig callableOperationWithoutOutParams() {
        OperationConfig config = new OperationConfig();
        OperationConfig.OperationDescriptor operationDescriptor = new OperationConfig.OperationDescriptor();
        operationDescriptor.setType(OperationConfig.OperationType.CALLABLE_STATEMENT);
        operationDescriptor.setDatabaseObjectName("test_pkg.test_proc");
        operationDescriptor.setInputParameters(List.of(param("ID", "INTEGER", 1)));
        config.setOperationDescriptor(operationDescriptor);
        return config;
    }
}
