package com.syv.RestJdbcProxy.controler;

import com.syv.RestJdbcProxy.service.AsyncService;
import com.syv.RestJdbcProxy.service.DynamicDataService;
import com.syv.RestJdbcProxy.config.DynamicDataSourceContextHolder;
import com.syv.RestJdbcProxy.init.AliasConfig;
import oracle.jdbc.proxy.annotation.Post;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class DynamicDataController {

    private static final Logger log = LoggerFactory.getLogger(DynamicDataController.class);
    private static final int THREAD_NUMBER = 10;

    @Autowired
    private DynamicDataService dynamicDataService;

    @Autowired
    public Map<String, AliasConfig> aliasConfigMap;


    @RequestMapping(value = "/batch/{aliasName}/**")
    public ResponseEntity<List<Map<String, Object>>> executeAliasBatch(@PathVariable String aliasName, @RequestBody List<Map<String, Object>> parameters) {
        return dynamicDataService.executeAliasBatch1( aliasName,  parameters);
    }

    @RequestMapping(value = "/dynpst/{aliasName}/**", method = RequestMethod.POST)
    public ResponseEntity<List<Map<String, Object>>> executeAliasP(@PathVariable String aliasName, @RequestBody Map<String, Object> parameters) {

        log.info("ResponseEntity parameters: connection: {}", parameters);
        String connection = (String) parameters.get("connection");
        String procName = aliasName;//(String) parameters.get("procName");
        parameters.remove("procName");

        AliasConfig aliasConfig = aliasConfigMap.get(procName);
        ResponseEntity<List<Map<String, Object>>> responseEntity = null;
        if (aliasConfig.getAlias().getPreparedStatementAlias() != null) {
            responseEntity = dynamicDataService.getResponseFromQuerySingle(parameters, aliasConfig);
        } else {
            responseEntity = dynamicDataService.getResponseFromSP(parameters, aliasConfig);
        }
        return responseEntity;
    }


    @GetMapping("/query")
    public ResponseEntity<List<Map<String, Object>>> executeDynamicQuery(@RequestParam(name = "connection") String connection, @RequestParam(name = "sqlQuery") String sqlQuery) {
        log.info("ResponseEntity parameters: connection: {}, sqlQuery: {}", connection, sqlQuery);
        DynamicDataSourceContextHolder.setDataSourceKey(connection);

        List<Map<String, Object>> result = dynamicDataService.executeDynamicQuery(sqlQuery);
        log.info("ResponseEntity result: {}", result);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    private String getPackagename(String spName) {
        String[] parts = spName.split("\\.");
        return parts[0];
    }

    private String getSpName(String spName) {
        String[] parts = spName.split("\\.");
        return parts[1];
    }
///// Async Task
    @Autowired
    private AsyncService asyncService;

    @PostMapping("/startAsyncTask/{aliasName}/**")
    public String startAsyncBatchTask(@PathVariable String aliasName, @RequestBody List<Map<String, Object>> parameters) {
        String taskId = UUID.randomUUID().toString();
        // asyncService.processAsync(taskId);
        //  return "Task submitted. Your task ID is: " + taskId;

       // return dynamicDataService.executeAliasBatch1( aliasName,  parameters);

        asyncService.processAsync(taskId, dynamicDataService, aliasName,  parameters);

  //      future.thenAccept(result -> System.out.println(result));

        return taskId;
    }
    @GetMapping("/taskResult/{taskId}")
    public  ResponseEntity<List<Map<String, Object>>> getTaskResult(@PathVariable String taskId) {
        ResponseEntity responseEntity = asyncService.getTaskResult(taskId);
        return responseEntity;
    }

    @GetMapping("/taskStatus/{taskId}")//return task status
    public String getTaskStatus(@PathVariable String taskId) {
        return asyncService.getTaskStatus(taskId);
    }
    @GetMapping("/tasksStatus")//return task status
    public Map<String, String> getTasksStatus() {
        return asyncService.getTasksStatus();
    }

    @GetMapping("/taskRemove/{taskId}")//remove task if done
    public String getTaskRemove(@PathVariable String taskId) {
        return asyncService.setTaskRemve(taskId);
    }


    @PostMapping("/startAsyncTaskTest")
    public String startAsyncTask() {
        String taskId = UUID.randomUUID().toString();
        // asyncService.processAsync(taskId);
        //  return "Task submitted. Your task ID is: " + taskId;

        CompletableFuture<  ResponseEntity<List<Map<String, Object>>>> future = asyncService.processAsyncTest1(taskId);

        future.thenAccept(result -> System.out.println(result));

        return "Task " + taskId + " started, check later for result.";
    }

}
