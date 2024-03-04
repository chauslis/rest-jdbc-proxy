package com.syv.RestJdbcProxy.controler;

import com.syv.RestJdbcProxy.service.DynamicDataService;
import com.syv.RestJdbcProxy.config.DynamicDataSourceContextHolder;
import com.syv.RestJdbcProxy.init.AliasConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

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
        log.info("ResponseEntity parameters:  {}", parameters);

        String procName = aliasName;//(String) parameters.get("procName");
        if (parameters.contains("connection"))
            parameters.remove("connectionconnection");
        if (parameters.contains("procName"))
            parameters.remove("procName");

        AliasConfig aliasConfig = aliasConfigMap.get(aliasName);
        ResponseEntity<List<Map<String, Object>>> responseEntity;

        // Iterate through each set of parameters and call the stored procedure
        if (aliasConfig.getAlias().getPreparedStatementAlias() != null) {
            responseEntity = getResponseFromQuery(parameters, aliasConfig);
        } else {
            responseEntity = getResponseFromBatchSP(parameters, aliasConfig);
        }
        return responseEntity;
    }

    private ResponseEntity<List<Map<String, Object>>> getResponseFromQuery1(Map<String, Object> parameters, AliasConfig aliasConfig) {
        String sqlStatementToPrepare = aliasConfig.getAlias().getPreparedStatementAlias().getSqlStatementToPrepare();
        List<Map<String, Object>> result = dynamicDataService.executeDynamicQuery(sqlStatementToPrepare, parameters);
        log.info("ResponseEntity result: {}", result);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    private ResponseEntity<List<Map<String, Object>>> getResponseFromQuery(List<Map<String, Object>> parameters, AliasConfig aliasConfig) {

        String sqlStatementToPrepare = aliasConfig.getAlias().getPreparedStatementAlias().getSqlStatementToPrepare();
        List<List<Map<String, Object>>> outParams = new ArrayList<>();
        try {
            outParams = dynamicDataService.distributeAndExecuteQuery(sqlStatementToPrepare, parameters, THREAD_NUMBER);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        List<Map<String, Object>> ret = outParams.stream().flatMap(List::stream).toList();
        log.debug("ResponseEntity result: {}", outParams);
        return new ResponseEntity<>(ret, HttpStatus.OK);
    }

    private ResponseEntity<List<Map<String, Object>>> getResponseFromBatchSP(List<Map<String, Object>> parameters, AliasConfig aliasConfig) {

        Map<String, String> inParamsDescr = new HashMap<>(convertParamList2Map(aliasConfig.getAlias().getCallableStatements().getInParam().getParam()));
        Map<String, String> outParamsDescr = new HashMap<>(convertParamList2Map(aliasConfig.getAlias().getCallableStatements().getOutParam().getParam()));

        String connection = (String) parameters.get(0).get("connection");//TBD add option work with different conne3ctions
        String spName = getSpName(aliasConfig);
        List<Map<String, Object>> outParams = new ArrayList<>();
        try {
            outParams = dynamicDataService.distributeAndExecuteSP(getPackagename(spName), getSpName(spName), inParamsDescr, parameters, outParamsDescr, THREAD_NUMBER);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        log.debug("ResponseEntity result: {}", outParams);
        return new ResponseEntity<>(outParams, HttpStatus.OK);
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
            responseEntity = getResponseFromQuery(parameters, aliasConfig);
        } else {
            responseEntity = getResponseFromSP(parameters, aliasConfig);
        }
        return responseEntity;
    }

    private Map<String, String> convertParamList2Map(List<AliasConfig.Param> formalPrams) {
        Map<String, String> formalPramsMap = new HashMap<>();
        formalPrams.stream().forEach(param -> {
            formalPramsMap.put(param.getJdbcParamName().toUpperCase(), param.getJdbcParamType().toUpperCase());
        });
        return formalPramsMap;
    }

    private ResponseEntity<List<Map<String, Object>>> getResponseFromSP(Map<String, Object> parameters, AliasConfig aliasConfig) {
        Map<String, String> inParamsDescr = new HashMap<>(convertParamList2Map(aliasConfig.getAlias().getCallableStatements().getInParam().getParam()));
        Map<String, String> outParamsDescr = new HashMap<>(convertParamList2Map(aliasConfig.getAlias().getCallableStatements().getOutParam().getParam()));

        String spName = getSpName(aliasConfig);
        Map<String, Object> outPartams = dynamicDataService.executeStoreFuncWithDynamicParams(getPackagename(spName), getSpName(spName), inParamsDescr, parameters, outParamsDescr);

        List<Map<String, Object>> out = new ArrayList<>();
        out.add(outPartams);
        log.debug("ResponseEntity result: {}", out);
        return new ResponseEntity<>(out, HttpStatus.OK);
    }

    private static String getSpName(AliasConfig aliasConfig) {
        String spName;
        String[] parts = aliasConfig.getAlias().getCallableStatements().getDbSpName().split("[\\s,()]+");
        if (parts[0].toUpperCase().equals("CALL")) {
            if (parts[1].toUpperCase().equals("?")) {
                spName = parts[3];
                String paramFromProc = parts[4];
            } else {
                spName = parts[1];
                String paramFromProc = parts[2];
            }
        } else spName = parts[0];
        return spName;
    }

    private ResponseEntity<List<Map<String, Object>>> getResponseFromQuery(Map<String, Object> parameters, AliasConfig aliasConfig) {
        String sqlStatementToPrepare = aliasConfig.getAlias().getPreparedStatementAlias().getSqlStatementToPrepare();
        List<Map<String, Object>> result = dynamicDataService.executeDynamicQuery(sqlStatementToPrepare, parameters);
        log.info("ResponseEntity result: {}", result);
        return new ResponseEntity<>(result, HttpStatus.OK);
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

}
