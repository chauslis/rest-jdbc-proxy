package com.syv.RestJdbcProxy.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncServiceTest {

    private final DynamicDataService dynamicDataService = mock(DynamicDataService.class);
    private final ExecutorService executorService = new SameThreadExecutorService();
    private AsyncService asyncService;

    @BeforeEach
    void setUp() {
        asyncService = new AsyncService();
        ReflectionTestUtils.setField(asyncService, "dynamicDataService", dynamicDataService);
        ReflectionTestUtils.setField(asyncService, "executorService", executorService);
        ReflectionTestUtils.setField(asyncService, "taskTtlMillis", 300000L);
    }

    @Test
    void processAsyncStoresCompletedTaskAndReturnsResult() {
        List<Map<String, Object>> parameters = List.of(Map.of("connection", "DB1", "id", 1));
        ResponseEntity<List<Map<String, Object>>> expected =
                new ResponseEntity<>(List.of(Map.of("result", "ok")), HttpStatus.OK);
        when(dynamicDataService.executeAliasBatch("alias", parameters)).thenReturn(expected);

        asyncService.processAsync("task-1", "alias", parameters);

        assertEquals(AsyncService.TASK_IS_COMPLETED, asyncService.getTaskStatus("task-1"));
        assertEquals(expected, asyncService.getTaskResult("task-1"));
        assertEquals(Map.of("task-1", AsyncService.TASK_IS_COMPLETED), asyncService.getTasksStatus());
        verify(dynamicDataService).executeAliasBatch("alias", parameters);
    }

    @Test
    void removeCompletedTaskAndReportMissingTask() {
        ResponseEntity<List<Map<String, Object>>> expected =
                new ResponseEntity<>(List.of(Map.of("result", "ok")), HttpStatus.OK);
        when(dynamicDataService.executeAliasBatch("alias", List.of())).thenReturn(expected);

        asyncService.processAsync("task-1", "alias", List.of());

        assertEquals(AsyncService.TASK_IS_COMPLETED, asyncService.setTaskRemve("task-1"));
        assertEquals(AsyncService.TASK_NOT_FOUND, asyncService.getTaskStatus("task-1"));
        assertEquals(AsyncService.TASK_NOT_FOUND, asyncService.setTaskRemve("task-1"));
        assertEquals(AsyncService.TASK_NOT_FOUND, asyncService.getTaskStatus(null));
    }

    @Test
    void reportsCancelledAndInProgressTasks() {
        CompletableFuture<ResponseEntity<List<Map<String, Object>>>> cancelled = new CompletableFuture<>();
        cancelled.cancel(false);
        CompletableFuture<ResponseEntity<List<Map<String, Object>>>> inProgress = new CompletableFuture<>();

        asyncService.putTask("cancelled", cancelled);
        asyncService.putTask("running", inProgress);

        assertEquals(AsyncService.TASK_IS_CANCELLED, asyncService.getTaskStatus("cancelled"));
        assertEquals(AsyncService.TASK_IS_IN_PROGRESS, asyncService.getTaskStatus("running"));
        assertEquals(AsyncService.TASK_IS_COMPLETED, asyncService.setTaskRemve("cancelled"));
        assertEquals(AsyncService.TASK_IS_IN_PROGRESS, asyncService.setTaskRemve("running"));
    }

    @Test
    void removeExpiredTasksDeletesOnlyTerminalTasksAfterTtl() {
        ReflectionTestUtils.setField(asyncService, "taskTtlMillis", 0L);
        CompletableFuture<ResponseEntity<List<Map<String, Object>>>> completed =
                CompletableFuture.completedFuture(ResponseEntity.ok(List.of(Map.of("result", "ok"))));
        CompletableFuture<ResponseEntity<List<Map<String, Object>>>> running = new CompletableFuture<>();

        asyncService.putTask("completed", completed);
        asyncService.putTask("running", running);

        assertEquals(1, asyncService.removeExpiredTasks());
        assertEquals(AsyncService.TASK_NOT_FOUND, asyncService.getTaskStatus("completed"));
        assertEquals(AsyncService.TASK_IS_IN_PROGRESS, asyncService.getTaskStatus("running"));
    }

    @Test
    void negativeTtlDisablesAutomaticCleanup() {
        ReflectionTestUtils.setField(asyncService, "taskTtlMillis", -1L);
        CompletableFuture<ResponseEntity<List<Map<String, Object>>>> completed =
                CompletableFuture.completedFuture(ResponseEntity.ok(List.of(Map.of("result", "ok"))));

        asyncService.putTask("completed", completed);

        assertEquals(0, asyncService.removeExpiredTasks());
        assertEquals(AsyncService.TASK_IS_COMPLETED, asyncService.getTaskStatus("completed"));
    }
}
