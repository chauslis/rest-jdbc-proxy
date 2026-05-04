package com.syv.RestJdbcProxy.controler;

import com.syv.RestJdbcProxy.config.DynamicDataSourceContextHolder;
import com.syv.RestJdbcProxy.init.AliasConfig;
import com.syv.RestJdbcProxy.service.AsyncService;
import com.syv.RestJdbcProxy.service.DynamicDataService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicDataControllerUnitTest {

    private final DynamicDataService dynamicDataService = mock(DynamicDataService.class);
    private final AsyncService asyncService = mock(AsyncService.class);
    private DynamicDataController controller;

    @BeforeEach
    void setUp() {
        controller = new DynamicDataController();
        ReflectionTestUtils.setField(controller, "dynamicDataService", dynamicDataService);
        ReflectionTestUtils.setField(controller, "asyncService", asyncService);
        ReflectionTestUtils.setField(controller, "demoMode", true);
    }

    @AfterEach
    void tearDown() {
        DynamicDataSourceContextHolder.clearDataSourceKey();
    }

    @Test
    void executeDynamicQuerySetsDataSourceAndReturnsServiceResult() {
        List<Map<String, Object>> rows = List.of(Map.of("ID", 1));
        when(dynamicDataService.executeDynamicQuery("select 1")).thenReturn(rows);

        ResponseEntity<List<Map<String, Object>>> response = controller.executeDynamicQuery("DB3", "select 1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(rows, response.getBody());
        assertEquals(null, DynamicDataSourceContextHolder.getDataSourceKey());
    }

    @Test
    void executeDynamicQueryRejectsRequestWhenDemoModeIsDisabled() {
        ReflectionTestUtils.setField(controller, "demoMode", false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.executeDynamicQuery("DB3", "select 1")
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void batchEndpointDelegatesToService() {
        List<Map<String, Object>> parameters = List.of(Map.of("connection", "DB1"));
        ResponseEntity<List<Map<String, Object>>> expected = ResponseEntity.ok(List.of(Map.of("result", "ok")));
        when(dynamicDataService.executeAliasBatch("alias", parameters)).thenReturn(expected);

        assertEquals(expected, controller.executeAliasBatch("alias", parameters));
    }

    @Test
    void asyncEndpointsDelegateToAsyncService() {
        when(asyncService.getTaskStatus("task-1")).thenReturn(AsyncService.TASK_IS_COMPLETED);
        when(asyncService.getTasksStatus()).thenReturn(Map.of("task-1", AsyncService.TASK_IS_COMPLETED));
        when(asyncService.getTaskResult("task-1")).thenReturn(ResponseEntity.ok(List.of(Map.of("result", "ok"))));
        when(asyncService.setTaskRemve("task-1")).thenReturn(AsyncService.TASK_IS_COMPLETED);

        assertEquals(AsyncService.TASK_IS_COMPLETED, controller.getTaskStatus("task-1"));
        assertEquals(Map.of("task-1", AsyncService.TASK_IS_COMPLETED), controller.getTasksStatus());
        assertEquals(ResponseEntity.ok(List.of(Map.of("result", "ok"))), controller.getTaskResult("task-1"));
        assertEquals(AsyncService.TASK_IS_COMPLETED, controller.getTaskRemove("task-1"));
    }

    @Test
    void dynpstPreparedStatementDelegatesToQueryResponse() {
        AliasConfig aliasConfig = preparedStatementAlias();
        ReflectionTestUtils.setField(controller, "aliasConfigMap", Map.of("prepared", aliasConfig));
        Map<String, Object> parameters = new java.util.HashMap<>(Map.of("connection", "DB1", "ID", 7));
        ResponseEntity<List<Map<String, Object>>> expected = ResponseEntity.ok(List.of(Map.of("ID", 7)));
        when(dynamicDataService.getResponseFromQuerySingle(parameters, aliasConfig)).thenReturn(expected);

        assertEquals(expected, controller.executeAliasP("prepared", parameters));
        verify(dynamicDataService).getResponseFromQuerySingle(parameters, aliasConfig);
    }

    @Test
    void dynpstMissingAliasReturnsNotFound() {
        ReflectionTestUtils.setField(controller, "aliasConfigMap", Map.of());
        Map<String, Object> parameters = new java.util.HashMap<>(Map.of("connection", "DB1", "ID", 7));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.executeAliasP("missing", parameters)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    private static AliasConfig preparedStatementAlias() {
        AliasConfig config = new AliasConfig();
        AliasConfig.Alias alias = new AliasConfig.Alias();
        alias.setPreparedStatementAlias(new AliasConfig.PreparedStatement());
        config.setAlias(alias);
        return config;
    }
}
