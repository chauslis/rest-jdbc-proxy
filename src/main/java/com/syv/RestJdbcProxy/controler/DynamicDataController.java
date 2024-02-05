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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
//@RequestMapping("/api/dynamic")
public class DynamicDataController {

    private static final Logger log = LoggerFactory.getLogger(DynamicDataController.class);

    @Autowired
    private DynamicDataService dynamicDataService;

    @Autowired
    public Map<String, AliasConfig> aliasConfigMap;


    @RequestMapping(value = "/dynpst/{aliasName}/**", method = RequestMethod.POST)
    public ResponseEntity<List<Map<String, Object>>> executeAliasP(@PathVariable String aliasName, @RequestBody Map<String, Object> parameters) {

        log.info("ResponseEntity parameters: connection: {}", parameters);
        String connection = (String) parameters.get("connection");
        DynamicDataSourceContextHolder.setDataSourceKey(connection);
        String procName = aliasName;//(String) parameters.get("procName");
        parameters.remove("connection");
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

    private Map<String,String> convertParamList2Map (List<AliasConfig.Param>  formalPrams){
        Map<String,String> formalPramsMap = new HashMap<>();
        formalPrams.stream().forEach(param -> {
            formalPramsMap.put(param.getJdbcParamName().toUpperCase(), param.getJdbcParamType().toUpperCase());
        });
        return formalPramsMap;
    }
    private ResponseEntity<List<Map<String, Object>>> getResponseFromSP(Map<String, Object> parameters, AliasConfig aliasConfig) {
        Map<String, String> inParamsDescr = new HashMap<>(convertParamList2Map(aliasConfig.getAlias().getCallableStatements().getInParam().getParam()));
        Map<String, String> outParamsDescr = new HashMap<>(convertParamList2Map(aliasConfig.getAlias().getCallableStatements().getOutParam().getParam()));


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
        } else
            spName = parts[0];

        Map<String, Object> outPartams = new HashMap<>();

        outPartams = dynamicDataService.executeStoreFuncWithDynamicParams(getPackagename(spName), getSpName(spName), inParamsDescr, parameters, outParamsDescr);
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(outPartams);
        log.debug("ResponseEntity result: {}", out);
        return new ResponseEntity<>(out, HttpStatus.OK);
    }

    private ResponseEntity<List<Map<String, Object>>> getResponseFromQuery(Map<String, Object> parameters, AliasConfig aliasConfig) {
        Map<String, Object> inParams = new HashMap<>();
        aliasConfig.getAlias().getPreparedStatementAlias().getInParam().getParam().stream().forEach(param -> {
            inParams.put(param.getJdbcParamName().toUpperCase(), parameters.get(param.getJdbcParamType().toUpperCase()));
        });

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
