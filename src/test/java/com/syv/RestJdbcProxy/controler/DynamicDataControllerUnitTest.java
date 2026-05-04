package com.syv.RestJdbcProxy.controler;

import com.syv.RestJdbcProxy.config.DynamicDataSourceContextHolder;
import com.syv.RestJdbcProxy.dto.GatewayMetadata;
import com.syv.RestJdbcProxy.dto.GatewayRequest;
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
        List<GatewayRequest> requests = List.of(gatewayRequest("DB1", Map.of("ID", 1)));
        ResponseEntity<List<Map<String, Object>>> expected = ResponseEntity.ok(List.of(Map.of("result", "ok")));
        when(dynamicDataService.executeAliasBatch("alias", requests)).thenReturn(expected);

        assertEquals(expected, controller.executeAliasBatch("alias", requests));
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
        GatewayRequest request = gatewayRequest("DB1", Map.of("ID", 7));
        ResponseEntity<List<Map<String, Object>>> expected = ResponseEntity.ok(List.of(Map.of("ID", 7)));
        when(dynamicDataService.executeAliasSingle("prepared", request)).thenReturn(expected);

        assertEquals(expected, controller.executeAliasP("prepared", request));
    }

    @Test
    void dynpstMissingAliasReturnsNotFound() {
        GatewayRequest request = gatewayRequest("DB1", Map.of("ID", 7));
        when(dynamicDataService.executeAliasSingle("missing", request))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Alias not found: missing"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.executeAliasP("missing", request)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    private static GatewayRequest gatewayRequest(String connectionName, Map<String, Object> params) {
        GatewayMetadata metadata = new GatewayMetadata();
        metadata.setConnectionName(connectionName);
        GatewayRequest request = new GatewayRequest();
        request.setRjp(metadata);
        request.setParams(params);
        return request;
    }
}
