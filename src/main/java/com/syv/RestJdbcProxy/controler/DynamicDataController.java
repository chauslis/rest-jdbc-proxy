package com.syv.RestJdbcProxy.controler;

import com.syv.RestJdbcProxy.service.AsyncService;
import com.syv.RestJdbcProxy.service.DynamicDataService;
import com.syv.RestJdbcProxy.config.DynamicDataSourceContextHolder;
import com.syv.RestJdbcProxy.dto.BatchExecutionResponse;
import com.syv.RestJdbcProxy.dto.GatewayRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class DynamicDataController {

    private static final Logger log = LoggerFactory.getLogger(DynamicDataController.class);

    @Autowired
    private DynamicDataService dynamicDataService;

    @Value("${app.demo-mode:false}")
    private boolean demoMode;


    @PostMapping(value = "/batch/{aliasName}/**")
    public ResponseEntity<BatchExecutionResponse> executeAliasBatch(@PathVariable String aliasName, @RequestBody List<GatewayRequest> requests) {
        return dynamicDataService.executeAliasBatch(aliasName, requests);
    }

    @RequestMapping(value = "/dynpst/{aliasName}/**", method = RequestMethod.POST)
    public ResponseEntity<List<Map<String, Object>>> executeAliasP(@PathVariable String aliasName, @RequestBody GatewayRequest request) {
        log.info("Gateway request received for alias: {}", aliasName);
        return dynamicDataService.executeAliasSingle(aliasName, request);
    }


    @GetMapping("/query")
    public ResponseEntity<List<Map<String, Object>>> executeDynamicQuery(@RequestParam(name = "connection") String connection, @RequestParam(name = "sqlQuery") String sqlQuery) {
        if (!demoMode) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Raw SQL endpoint is available only when app.demo-mode=true");
        }
        log.info("ResponseEntity parameters: connection: {}, sqlQuery: {}", connection, sqlQuery);
        DynamicDataSourceContextHolder.setDataSourceKey(connection);

        try {
            List<Map<String, Object>> result = dynamicDataService.executeDynamicQuery(sqlQuery);
            log.info("ResponseEntity result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } finally {
            DynamicDataSourceContextHolder.clearDataSourceKey();
        }
    }

    @Autowired
    private AsyncService asyncService;

    @PostMapping("/startAsyncTask/{aliasName}/**")
    public String startAsyncBatchTask(@PathVariable String aliasName, @RequestBody List<GatewayRequest> requests) {
        String taskId = UUID.randomUUID().toString();
        asyncService.processAsync(taskId, aliasName, requests);
        return taskId;
    }
    @GetMapping("/taskResult/{taskId}")
    public ResponseEntity<BatchExecutionResponse> getTaskResult(@PathVariable String taskId) {
        return asyncService.getTaskResult(taskId);
    }

    @GetMapping("/taskStatus/{taskId}")
    public String getTaskStatus(@PathVariable String taskId) {
        return asyncService.getTaskStatus(taskId);
    }
    @GetMapping("/tasksStatus")
    public Map<String, String> getTasksStatus() {
        return asyncService.getTasksStatus();
    }

    @GetMapping("/taskRemove/{taskId}")
    public String getTaskRemove(@PathVariable String taskId) {
        return asyncService.setTaskRemve(taskId);
    }


    @PostMapping("/startAsyncTaskTest")
    public String startAsyncTask() {
        String taskId = UUID.randomUUID().toString();
        CompletableFuture<ResponseEntity<BatchExecutionResponse>> future = asyncService.processAsyncTest1(taskId);
        future.thenAccept(result -> System.out.println(result));
        return "Task " + taskId + " started, check later for result.";
    }

}
