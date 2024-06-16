package com.syv.RestJdbcProxy.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

@Service
public class AsyncService {


    public static final String TASK_IS_COMPLETED = "Task is completed";
    public static final String TASK_IS_CANCELLED = "Task is cancelled";
    public static final String TASK_IS_IN_PROGRESS = "Task is in progress";
    public static final String TASK_NOT_FOUND = "Task not found";
    //private final Map<String, CompletableFuture<String  ResponseEntity<List<Map<String, Object>>>>> tasks = new ConcurrentHashMap<>();

  //  @Autowired
    private final Map<String,CompletableFuture<  ResponseEntity<List<Map<String, Object>>>>>  tasks = new ConcurrentHashMap<>();

    @Async
    public CompletableFuture<  ResponseEntity<List<Map<String, Object>>>> processAsyncTest1(String taskId) {
        // Simulate a long-running task


        return tasks.put(taskId,  CompletableFuture.supplyAsync(() -> {
                ResponseEntity<List<Map<String, Object>>> result = null;
                try {
                    result = new ResponseEntity<>(Arrays.asList(
                            Map.of( "Field_1", "Test 1",
                                    "Field_2", "John Doe",
                                    "Field_3", 30)
                    ), HttpStatus.OK);
                    Thread.sleep(30000); // 5 seconds delay

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return result;
                }
            )
        );



    }

    public ResponseEntity<List<Map<String, Object>>> getTaskResult(String taskId) {
        try {
            return tasks.get(taskId).get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
//    @GetMapping("/taskSratus/{taskId}")
    public  String getTaskStatus(String taskId) {
        if (taskId == null ||tasks.get(taskId) == null) {
            return TASK_NOT_FOUND;
        }else if (tasks.get(taskId).isDone()) {
            return TASK_IS_COMPLETED;
        } else if (tasks.get(taskId).isCancelled()) {
            return TASK_IS_CANCELLED;
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
                        if (entry.getValue().isDone()) {
                            st = TASK_IS_COMPLETED;
                        } else if (entry.getValue().isCancelled()) {
                            st = TASK_IS_CANCELLED;
                        } else {
                            st = TASK_IS_IN_PROGRESS;
                        };
                        status.put(entry.getKey(), st);
                    }
            );
        return status;
    }
    @Async
    public  void processAsync(String taskId, DynamicDataService dynamicDataService,  String aliasName, List<Map<String, Object>> parameters) {
        tasks.put(taskId,  CompletableFuture.supplyAsync(() -> {
                    ResponseEntity<List<Map<String, Object>>> result = null;
                    try {
                            result = dynamicDataService.executeAliasBatch1( aliasName,  parameters);
                                Thread.sleep(15000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return result;
                        }
                )
        );

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
}
