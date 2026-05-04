package com.syv.RestJdbcProxy.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

@Service
public class AsyncService {


    public static final String TASK_IS_COMPLETED = "Task is completed";
    public static final String TASK_IS_CANCELLED = "Task is cancelled";
    public static final String TASK_IS_IN_PROGRESS = "Task is in progress";
    public static final String TASK_NOT_FOUND = "Task not found";
    private final Map<String, TaskRecord> tasks = new ConcurrentHashMap<>();

    @Autowired
    private DynamicDataService dynamicDataService;

    @Autowired
    private ExecutorService executorService;

    @Value("${app.async.task-ttl-ms:300000}")
    private long taskTtlMillis;

    public CompletableFuture<  ResponseEntity<List<Map<String, Object>>>> processAsyncTest1(String taskId) {
        // Simulate a long-running task

        CompletableFuture<ResponseEntity<List<Map<String, Object>>>> future = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new ResponseEntity<>(List.of(
                    Map.of("Field_1", "Test 1",
                            "Field_2", "John Doe",
                    "Field_3", 30)
            ), HttpStatus.OK);
        }, executorService);
        putTask(taskId, future);
        return future;
    }

    public ResponseEntity<List<Map<String, Object>>> getTaskResult(String taskId) {
        try {
            return tasks.get(taskId).future().get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
//    @GetMapping("/taskSratus/{taskId}")
    public  String getTaskStatus(String taskId) {
        TaskRecord taskRecord = taskId == null ? null : tasks.get(taskId);
        if (taskRecord == null) {
            return TASK_NOT_FOUND;
        }
        CompletableFuture<ResponseEntity<List<Map<String, Object>>>> future = taskRecord.future();
        if (future.isCancelled()) {
            return TASK_IS_CANCELLED;
        } else if (future.isDone()) {
            return TASK_IS_COMPLETED;
        } else {
            return TASK_IS_IN_PROGRESS;
        }
    }

    public Map<String, String> getTasksStatus() {
        Map<String, String> status = new ConcurrentHashMap<>();
        tasks
            .entrySet()
            .stream()
            .forEach(
                    entry -> {
                        String st;
                        CompletableFuture<ResponseEntity<List<Map<String, Object>>>> future = entry.getValue().future();
                        if (future.isCancelled()) {
                            st = TASK_IS_CANCELLED;
                        } else if (future.isDone()) {
                            st = TASK_IS_COMPLETED;
                        } else {
                            st = TASK_IS_IN_PROGRESS;
                        };
                        status.put(entry.getKey(), st);
                    }
            );
        return status;
    }
    public void processAsync(String taskId, String aliasName, List<Map<String, Object>> parameters) {
        CompletableFuture<ResponseEntity<List<Map<String, Object>>>> future = CompletableFuture.supplyAsync(
                () -> dynamicDataService.executeAliasBatch(aliasName, parameters),
                executorService
        );
        putTask(taskId, future);
    }

    public String setTaskRemve(String taskId) {
        if (tasks.get(taskId) == null) {
            return TASK_NOT_FOUND;
        }
        String status = getTaskStatus(taskId);
        if (status.equals(TASK_IS_COMPLETED) || status.equals(TASK_IS_CANCELLED)) {
            tasks.remove(taskId);
            return TASK_IS_COMPLETED;
        } else {
            return TASK_IS_IN_PROGRESS;
        }
    }

    @Scheduled(fixedDelayString = "${app.async.task-cleanup-interval-ms:60000}")
    public int removeExpiredTasks() {
        if (taskTtlMillis < 0) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int initialSize = tasks.size();
        tasks.entrySet().removeIf(entry -> entry.getValue().isExpired(now, taskTtlMillis));
        return initialSize - tasks.size();
    }

    void putTask(String taskId, CompletableFuture<ResponseEntity<List<Map<String, Object>>>> future) {
        TaskRecord taskRecord = new TaskRecord(future);
        future.whenComplete((result, throwable) -> taskRecord.markCompleted());
        tasks.put(taskId, taskRecord);
    }

    private static class TaskRecord {
        private final CompletableFuture<ResponseEntity<List<Map<String, Object>>>> future;
        private volatile Long completedAtMillis;

        private TaskRecord(CompletableFuture<ResponseEntity<List<Map<String, Object>>>> future) {
            this.future = future;
            if (future.isDone() || future.isCancelled()) {
                markCompleted();
            }
        }

        private CompletableFuture<ResponseEntity<List<Map<String, Object>>>> future() {
            return future;
        }

        private void markCompleted() {
            if (completedAtMillis == null) {
                completedAtMillis = System.currentTimeMillis();
            }
        }

        private boolean isExpired(long now, long ttlMillis) {
            return completedAtMillis != null && now - completedAtMillis >= ttlMillis;
        }
    }
}
