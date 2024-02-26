package com.syv.RestJdbcProxy.service;



import com.syv.RestJdbcProxy.config.DynamicDataSourceContextHolder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Service
public class DynamicDataService {
    private static final Logger log = LoggerFactory.getLogger(DynamicDataService.class);
    private JdbcTemplate jdbcTemplate = new JdbcTemplate();;


    @Autowired
    ExecutorService executorService;
    @Bean
    public DataSourceTransactionManager transactionManager(DataSource dynamicDataSource) {
        return new DataSourceTransactionManager(dynamicDataSource);
    }

    @Autowired
    public void setDS(DataSource dataSource) {
        this.jdbcTemplate.setDataSource(dataSource);
    }
    public List<Map<String, Object>> executeDynamicQuery(String sqlQuery) {
        return jdbcTemplate.queryForList(sqlQuery);
    }


    @Async
    public CompletableFuture<List<Map<String, Object>>> callStoredProcedureexecuteStoreFuncWithDynamicParams(String connection,
                                                                                                             String catalog,
                                                                                                             String storedProcName,
                                                                                                             Map<String, String> formalInParams,
                                                                                                             List<Map<String, Object>>  inParams,
                                                                                                             Map<String, String> formalOutParams) {//tbd add batch of outParams

        CompletableFuture<List<Map<String, Object>>> future = CompletableFuture.supplyAsync(() -> {
            return inParams.stream()
                    .map(param -> executeStoreFuncWithDynamicParams(connection, catalog, storedProcName, formalInParams, param, formalOutParams))
                    .collect( Collectors.toList());
        });

        return future;
    }

    public List<Map<String, Object>> distributeAndExecute(String connection,
                                                          String catalog,
                                                         String storedProcName,
                                                         Map<String, String> formalInParams,
                                                         List<Map<String, Object>> inParams,
                                                         Map<String, String> formalOutParams,
                                                         int numberOfThreads) throws ExecutionException, InterruptedException {

        List<CompletableFuture<List<Map<String,Object>>>> futureList = new LinkedList<>();
        int partitionSize = inParams.size() / numberOfThreads;
        partitionSize = partitionSize < 1 ? numberOfThreads : partitionSize;
        int end = 0;
        for (int i = 0; i < numberOfThreads; i++) {
            int start = i * partitionSize;
            end = (i + 1) * partitionSize;
            List<Map<String, Object>> partition = inParams.subList(start, end);
            futureList.add(callStoredProcedureexecuteStoreFuncWithDynamicParams(connection, catalog, storedProcName, formalInParams, partition, formalOutParams));
        }
        if(end < inParams.size())
            futureList.add(callStoredProcedureexecuteStoreFuncWithDynamicParams(connection, catalog, storedProcName, formalInParams, inParams.subList(end, inParams.size()), formalOutParams));

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0]));
        CompletableFuture<List<List<Map<String, Object>>> > allFutureResults = allFutures.thenApply(v ->
                            futureList.stream()
                            .map(CompletableFuture::join) // Retrieves the result of the computation
                            .collect(Collectors.toList())
        );
        List<Map<String, Object>>
        results = allFutureResults.get().stream().flatMap(List::stream).collect(Collectors.toList());

        return results;
    }
    public List<Map<String, Object>> executeDynamicQuery(String sqlQuery, Map<String, Object> inParams) {
        List<Object> parameters =  inParams.entrySet().stream().map(param -> param.getValue()).collect(Collectors.toList());
        return jdbcTemplate.queryForList(sqlQuery, parameters.toArray());
    }
    public Map<String, Object> executeDynamicQueryWithOutParams(String sqlQuery) {
        return jdbcTemplate.queryForMap(sqlQuery);
    }
  //  @Transactional
    public Map<String, Object> executeStoreFuncWithDynamicParams(String connection, String catalog, String storedProcName, Map<String, String> formalInParams, Map<String, Object> inParams, Map<String, String> formalOutParams) {
        SimpleJdbcCall simpleJdbcCall = new SimpleJdbcCall(jdbcTemplate)
                .withCatalogName(catalog);
                //.withProcedureName(storedProcName)
                //.withFunctionName (storedProcName);
        if (connection != null)
            DynamicDataSourceContextHolder.setDataSourceKey(connection);//toDo use connection from paramters
        else
            DynamicDataSourceContextHolder.setDataSourceKey(inParams.get("connection").toString());
        inParams.remove("connection");
        String outParamsType = null;
        if (formalOutParams != null && formalOutParams.size() > 0 && formalOutParams.containsKey("RESULT")){
            simpleJdbcCall.withFunctionName(storedProcName);
            outParamsType = formalOutParams.get("RESULT");
//            formalOutParams.remove("RESULT");
        }else
            simpleJdbcCall.withProcedureName(storedProcName);

        formalOutParams
                .entrySet()
                .stream()
                .filter(param -> !param.getKey().toUpperCase().equals("RESULT"))
                .forEach(param -> { simpleJdbcCall.declareParameters(
                        new SqlOutParameter(param.getKey().toUpperCase(),
                                convertStringToJdbcType(param.getValue().toUpperCase()))); });

        formalInParams
                .entrySet()
                .stream()
                .forEach(param -> { simpleJdbcCall.declareParameters(
                        new SqlParameter(param.getKey().toUpperCase(),
                                convertStringToJdbcType(param.getValue().toUpperCase()))); });
        Object result = null;
        Map<String, Object> outParams;
        if (outParamsType != null) {
            int tType = convertStringToJdbcType(outParamsType);
            Class<?> returnType = convertSqlTypeToJavaClass(tType);
            outParams = new HashMap<>();
            result = simpleJdbcCall.executeFunction(convertSqlTypeToJavaClass(tType), inParams);
            log.debug("Result: {}", result);
            outParams.put("result", result);// todo: support differnt out paranmetr function result
        }else{
            outParams =  simpleJdbcCall.execute(inParams);
        }
        return outParams;

    }

    @Transactional
    public Map<String, Object> executeStoreFuncWithDynamicParams(String catalog, String storedProcName, Map<String, String> formalParams, Map<String, Object> inParams, String outParamsType) {
        SimpleJdbcCall simpleJdbcCall = new SimpleJdbcCall(jdbcTemplate)
                .withCatalogName(catalog)
                //.withProcedureName(storedProcName)
                .withFunctionName (storedProcName);

        if (outParamsType != null){
            simpleJdbcCall.withFunctionName(storedProcName);
            simpleJdbcCall.declareParameters(new SqlOutParameter("result", convertStringToJdbcType(outParamsType)));
        }else
            simpleJdbcCall.withProcedureName(storedProcName);

        formalParams
                .entrySet()
                .stream()
                .forEach(param -> { simpleJdbcCall.declareParameters(
                        new SqlParameter(param.getKey().toUpperCase(),
                                convertStringToJdbcType(param.getValue().toUpperCase()))); });
        Object result = null;
        if (outParamsType != null) {
            int tType = convertStringToJdbcType(outParamsType);
            Class<?> returnType = convertSqlTypeToJavaClass(tType);
            result = simpleJdbcCall.executeFunction(convertSqlTypeToJavaClass(tType), inParams);
        }else{
            simpleJdbcCall.execute(inParams);
        }
        Map<String, Object> outParams   = new HashMap<>();
        outParams.put("result", result);
        return outParams;

    }


    public static int convertStringToJdbcType(String jdbcTypeName) {
        switch (jdbcTypeName.toUpperCase()) {
            case "VARCHAR":
                return Types.VARCHAR;
            case "INTEGER":
                return Types.INTEGER;
            case "BIGINT":
                return Types.BIGINT;
            case "DOUBLE":
                return Types.DOUBLE;
            // Add more type mappings as needed
            default:
                throw new IllegalArgumentException("Unsupported JDBC type: " + jdbcTypeName);
        }
    }

    public static Class<?> convertSqlTypeToJavaClass(int sqlType) {//java.sql.Type to javaType.class
        int iType = Types.INTEGER;
        switch (iType) {
            case Types.INTEGER:
                return Integer.class;
            case Types.BIGINT:
                return Long.class;
            case Types.SMALLINT:
                return Short.class;
            case Types.FLOAT:
                return Float.class;
            case Types.DOUBLE:
                return Double.class;
            case Types.BOOLEAN:
            case Types.BIT:
                return Boolean.class;
            case Types.VARCHAR:
            case Types.CHAR:
            case Types.NVARCHAR:
            case Types.NCHAR:
                return String.class;
            case Types.DATE:
            case Types.TIME:
            case Types.TIMESTAMP:
                return java.sql.Timestamp.class;
            case Types.BLOB:
                return java.sql.Blob.class;
            case Types.CLOB:
                return java.sql.Clob.class;
            case Types.ARRAY:
                return java.sql.Array.class;
            case Types.STRUCT:
                return java.sql.Struct.class;
            case Types.REF:
                return java.sql.Ref.class;
            case Types.BINARY:
            case Types.VARBINARY:
                return byte[].class;
            default:
                return null; // Unknown SQL type
        }
    }

    @PreDestroy
    public void destroy() {
        executorService.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("ExecutorService did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            executorService.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }
}
