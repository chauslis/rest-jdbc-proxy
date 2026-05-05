package com.syv.RestJdbcProxy.service;

import com.syv.RestJdbcProxy.config.DynamicDataSourceContextHolder;
import com.syv.RestJdbcProxy.dto.BatchErrorCode;
import com.syv.RestJdbcProxy.dto.BatchErrorDetailsMode;
import com.syv.RestJdbcProxy.dto.BatchErrorRecord;
import com.syv.RestJdbcProxy.dto.BatchErrorRecordsMode;
import com.syv.RestJdbcProxy.dto.BatchExecutionResponse;
import com.syv.RestJdbcProxy.dto.ErrorInfo;
import com.syv.RestJdbcProxy.dto.GatewayMetadata;
import com.syv.RestJdbcProxy.dto.GatewayRequest;
import com.syv.RestJdbcProxy.init.OperationConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DynamicDataService {
    private static final Logger log = LoggerFactory.getLogger(DynamicDataService.class);
    private static final String ROUTING_RESPONSE_FIELD = "connectionName";
    private JdbcTemplate jdbcTemplate = new JdbcTemplate();

    @Autowired
    public Map<String, OperationConfig> operationConfigMap;

    @Autowired
    ExecutorService executorService;

    @Value("${app.batch.max-threads-per-request:10}")
    private int maxThreadsPerRequest;

    @Value("${app.batch.error-details-mode:CODE_AND_MESSAGE}")
    private String batchErrorDetailsMode;

    @Value("${app.batch.error-records-mode:FAILED_ONLY}")
    private String batchErrorRecordsMode;

    @PostConstruct
    void validateBatchErrorConfiguration() {
        errorDetailsMode();
        errorRecordsMode();
    }

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

    public CompletableFuture<List<RecordExecutionResult>> setTaskEecuteStoredPreocWithDynamicParams(
            String catalog,
            String storedProcName,
            List<OperationConfig.ParameterDescriptor> formalInParams,
            List<GatewayRequest> requests,
            List<OperationConfig.ParameterDescriptor> formalOutParams
    ) {
        return CompletableFuture.supplyAsync(
                () -> requests.stream()
                        .map(request -> executeStoreFuncBatchRecord(catalog, storedProcName, formalInParams, request, formalOutParams))
                        .collect(Collectors.toList()),
                executorService
        );
    }

    public CompletableFuture<List<RecordExecutionResult>> setTaskEecuteQueryDynamicParams(
            String sqlQuery,
            List<GatewayRequest> requests,
            List<OperationConfig.ParameterDescriptor> inputParameters
    ) {
        return CompletableFuture.supplyAsync(
                () -> requests.stream()
                        .map(request -> executeDynamicQueryBatchRecord(sqlQuery, request, inputParameters))
                        .collect(Collectors.toList()),
                executorService
        );
    }

    public List<RecordExecutionResult> distributeAndExecuteQuery(
            String sqlQuery,
            List<GatewayRequest> requests,
            List<OperationConfig.ParameterDescriptor> inputParameters,
            int numberOfThreads
    ) throws ExecutionException, InterruptedException {
        List<CompletableFuture<List<RecordExecutionResult>>> futureList = new LinkedList<>();
        splitList2sublists(requests, numberOfThreads).forEach(partition ->
                futureList.add(setTaskEecuteQueryDynamicParams(sqlQuery, partition, inputParameters))
        );

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0]));
        CompletableFuture<List<List<RecordExecutionResult>>> allFutureResults = allFutures.thenApply(v -> futureList.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()));
        return allFutureResults.get().stream().flatMap(List::stream).collect(Collectors.toList());
    }

    <T> Stream<List<T>> splitList2sublists(List<T> list, int threadNumber) {
        List<List<T>> sublists = new LinkedList<>();

        if (list == null || list.isEmpty()) {
            return sublists.stream();
        }

        int partitionCount = Math.min(Math.max(1, threadNumber), list.size());
        int basePartitionSize = list.size() / partitionCount;
        int remainder = list.size() % partitionCount;

        int start = 0;
        for (int i = 0; i < partitionCount; i++) {
            int partitionSize = basePartitionSize + (i < remainder ? 1 : 0);
            int end = start + partitionSize;
            sublists.add(list.subList(start, end));
            start = end;
        }

        return sublists.stream();
    }

    private int resolveBatchThreadCount(int itemCount) {
        return Math.max(1, Math.min(itemCount, maxThreadsPerRequest));
    }

    public List<RecordExecutionResult> distributeAndExecuteSP(
            String catalog,
            String storedProcName,
            List<OperationConfig.ParameterDescriptor> formalInParams,
            List<GatewayRequest> requests,
            List<OperationConfig.ParameterDescriptor> formalOutParams,
            int numberOfThreads
    ) throws ExecutionException, InterruptedException {
        List<CompletableFuture<List<RecordExecutionResult>>> futureList = new LinkedList<>();
        splitList2sublists(requests, numberOfThreads).forEach(partition ->
                futureList.add(setTaskEecuteStoredPreocWithDynamicParams(catalog, storedProcName, formalInParams, partition, formalOutParams))
        );

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0]));
        CompletableFuture<List<List<RecordExecutionResult>>> allFutureResults = allFutures.thenApply(v -> futureList.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()));
        return allFutureResults.get().stream().flatMap(List::stream).collect(Collectors.toList());
    }

    public List<Map<String, Object>> executeDynamicQuery(
            String sqlQuery,
            GatewayRequest request,
            List<OperationConfig.ParameterDescriptor> inputParameters
    ) {
        String connectionName = connectionName(request);
        Map<String, Object> params = params(request);
        DynamicDataSourceContextHolder.setDataSourceKey(connectionName);
        try {
            Object[] jdbcParameters = buildJdbcParameters(params, inputParameters);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sqlQuery, jdbcParameters);
            return rows.stream().map(row -> {
                Map<String, Object> rowCopy = new HashMap<>(row);
                rowCopy.put(ROUTING_RESPONSE_FIELD, connectionName);
                return rowCopy;
            }).collect(Collectors.toList());
        } finally {
            DynamicDataSourceContextHolder.clearDataSourceKey();
        }
    }

    public Map<String, Object> executeStoreFuncWithDynamicParams(
            String catalog,
            String storedProcName,
            List<OperationConfig.ParameterDescriptor> formalInParams,
            GatewayRequest request,
            List<OperationConfig.ParameterDescriptor> formalOutParams
    ) {
        String connectionName = connectionName(request);
        Map<String, Object> jdbcParams = jdbcParamsForCall(params(request), formalInParams);
        SimpleJdbcCall simpleJdbcCall = createSimpleJdbcCall().withCatalogName(catalog);

        DynamicDataSourceContextHolder.setDataSourceKey(connectionName);
        try {
            OperationConfig.ParameterDescriptor resultParameter = resultParameter(formalOutParams);
            if (resultParameter != null) {
                simpleJdbcCall.withFunctionName(storedProcName);
            } else {
                simpleJdbcCall.withProcedureName(storedProcName);
            }

            formalOutParams.stream()
                    .filter(param -> !isResultParameter(param))
                    .forEach(param -> simpleJdbcCall.declareParameters(
                            new SqlOutParameter(param.getName().toUpperCase(), convertStringToJdbcType(param.getJdbcType().toUpperCase()))
                    ));

            formalInParams.forEach(param -> simpleJdbcCall.declareParameters(
                    new SqlParameter(param.getName().toUpperCase(), convertStringToJdbcType(param.getJdbcType().toUpperCase()))
            ));

            if (resultParameter != null) {
                int tType = convertStringToJdbcType(resultParameter.getJdbcType());
                Object result = simpleJdbcCall.executeFunction(convertSqlTypeToJavaClass(tType), jdbcParams);
                Map<String, Object> outParams = new HashMap<>();
                outParams.put("result", result);
                return outParams;
            }
            return simpleJdbcCall.execute(jdbcParams);
        } finally {
            DynamicDataSourceContextHolder.clearDataSourceKey();
        }
    }

    protected SimpleJdbcCall createSimpleJdbcCall() {
        return new SimpleJdbcCall(jdbcTemplate);
    }

    public ResponseEntity<BatchExecutionResponse> getResponseFromQuery(List<GatewayRequest> requests, OperationConfig operationConfig) {
        OperationConfig.OperationDescriptor descriptor = operationConfig.getOperationDescriptor();
        List<RecordExecutionResult> recordResults;
        try {
            recordResults = distributeAndExecuteQuery(
                    descriptor.getSql(),
                    requests,
                    descriptor.getInputParameters(),
                    resolveBatchThreadCount(requests.size())
            );
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        BatchExecutionResponse response = batchExecutionResponse(recordResults);
        log.debug("ResponseEntity result: {}", response);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public ResponseEntity<BatchExecutionResponse> executeAliasBatch(String aliasName, List<GatewayRequest> requests) {
        validateRequests(requests);
        OperationConfig operationConfig = operationConfig(aliasName);
        OperationConfig.OperationDescriptor descriptor = operationConfig.getOperationDescriptor();

        if (descriptor.getType() == OperationConfig.OperationType.PREPARED_STATEMENT) {
            return getResponseFromQuery(requests, operationConfig);
        }
        return getResponseFromBatchSP(requests, operationConfig);
    }

    private ResponseEntity<BatchExecutionResponse> getResponseFromBatchSP(List<GatewayRequest> requests, OperationConfig operationConfig) {
        OperationConfig.OperationDescriptor descriptor = operationConfig.getOperationDescriptor();
        String spName = descriptor.getDatabaseObjectName();
        List<RecordExecutionResult> recordResults;
        try {
            recordResults = distributeAndExecuteSP(
                    getPackagename(spName),
                    getSpName(spName),
                    descriptor.getInputParameters(),
                    requests,
                    descriptor.getOutputParameters(),
                    resolveBatchThreadCount(requests.size())
            );
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        BatchExecutionResponse response = batchExecutionResponse(recordResults);
        log.debug("ResponseEntity result: {}", response);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    private RecordExecutionResult executeDynamicQueryBatchRecord(
            String sqlQuery,
            GatewayRequest request,
            List<OperationConfig.ParameterDescriptor> inputParameters
    ) {
        try {
            return RecordExecutionResult.success(request, executeDynamicQuery(sqlQuery, request, inputParameters));
        } catch (Exception e) {
            return RecordExecutionResult.failure(request, e);
        }
    }

    private RecordExecutionResult executeStoreFuncBatchRecord(
            String catalog,
            String storedProcName,
            List<OperationConfig.ParameterDescriptor> formalInParams,
            GatewayRequest request,
            List<OperationConfig.ParameterDescriptor> formalOutParams
    ) {
        try {
            Map<String, Object> result = executeStoreFuncWithDynamicParams(catalog, storedProcName, formalInParams, request, formalOutParams);
            return RecordExecutionResult.success(request, result == null ? List.of() : List.of(result));
        } catch (Exception e) {
            return RecordExecutionResult.failure(request, e);
        }
    }

    private BatchExecutionResponse batchExecutionResponse(List<RecordExecutionResult> recordResults) {
        List<BatchErrorRecord> errorRecords = new ArrayList<>();
        List<Map<String, Object>> results = new ArrayList<>();
        BatchErrorRecordsMode recordsMode = errorRecordsMode();

        recordResults.forEach(recordResult -> {
            if (recordResult.success()) {
                results.addAll(recordResult.results());
                if (recordsMode == BatchErrorRecordsMode.ALL) {
                    errorRecords.add(batchRecord(recordResult.request(), "SUCCESS", null));
                }
            } else {
                errorRecords.add(batchRecord(recordResult.request(), "FAILED", errorInfo(recordResult.exception())));
            }
        });

        return new BatchExecutionResponse(errorRecords, results);
    }

    private BatchErrorRecord batchRecord(GatewayRequest request, String status, ErrorInfo errorInfo) {
        BatchErrorRecord record = new BatchErrorRecord();
        record.setRjp(copyMetadata(request));
        record.setParams(copyParams(request));
        record.setStatus(status);
        record.setError(errorInfo);
        return record;
    }

    private GatewayMetadata copyMetadata(GatewayRequest request) {
        GatewayMetadata metadata = new GatewayMetadata();
        if (request != null && request.getRjp() != null) {
            metadata.setConnectionName(request.getRjp().getConnectionName());
        }
        return metadata;
    }

    private Map<String, Object> copyParams(GatewayRequest request) {
        if (request == null || request.getParams() == null) {
            return Map.of();
        }
        return new HashMap<>(request.getParams());
    }

    private ErrorInfo errorInfo(Exception exception) {
        BatchErrorCode errorCode = errorCode(exception);
        BatchErrorDetailsMode detailsMode = errorDetailsMode();
        if (detailsMode == BatchErrorDetailsMode.CODE_ONLY) {
            return new ErrorInfo(errorCode.name(), null, null);
        }
        if (detailsMode == BatchErrorDetailsMode.FULL) {
            return new ErrorInfo(errorCode.name(), sanitizedMessage(exception), exception.getClass().getSimpleName());
        }
        return new ErrorInfo(errorCode.name(), sanitizedMessage(exception), null);
    }

    private BatchErrorCode errorCode(Exception exception) {
        if (exception instanceof DataAccessException || hasCause(exception, SQLException.class)) {
            return BatchErrorCode.SQL_ERROR;
        }
        if (exception instanceof ResponseStatusException responseStatusException) {
            if (responseStatusException.getStatusCode() == HttpStatus.BAD_REQUEST) {
                return BatchErrorCode.BAD_REQUEST;
            }
            if (responseStatusException.getStatusCode() == HttpStatus.NOT_FOUND) {
                return BatchErrorCode.ALIAS_NOT_FOUND;
            }
        }
        if (exception instanceof IllegalArgumentException && exception.getMessage() != null
                && exception.getMessage().contains("Unsupported JDBC type")) {
            return BatchErrorCode.UNSUPPORTED_JDBC_TYPE;
        }
        if (exception instanceof IllegalArgumentException) {
            return BatchErrorCode.PARAMETER_MAPPING_ERROR;
        }
        if (exception instanceof TimeoutException || exception instanceof CancellationException
                || exception instanceof InterruptedException) {
            return BatchErrorCode.TIMEOUT;
        }
        String message = exception.getMessage();
        if (message != null && (message.contains("DataSource") || message.contains("lookup key"))) {
            return BatchErrorCode.CONNECTION_NOT_FOUND;
        }
        return BatchErrorCode.UNKNOWN_ERROR;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String sanitizedMessage(Exception exception) {
        if (exception.getMessage() == null) {
            return null;
        }
        String message = exception.getMessage().replaceAll("[\\r\\n\\t]+", " ").trim();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private BatchErrorDetailsMode errorDetailsMode() {
        if (batchErrorDetailsMode == null || batchErrorDetailsMode.isBlank()) {
            return BatchErrorDetailsMode.CODE_AND_MESSAGE;
        }
        try {
            return BatchErrorDetailsMode.valueOf(batchErrorDetailsMode);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid app.batch.error-details-mode: " + batchErrorDetailsMode, e);
        }
    }

    private BatchErrorRecordsMode errorRecordsMode() {
        if (batchErrorRecordsMode == null || batchErrorRecordsMode.isBlank()) {
            return BatchErrorRecordsMode.FAILED_ONLY;
        }
        try {
            return BatchErrorRecordsMode.valueOf(batchErrorRecordsMode);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid app.batch.error-records-mode: " + batchErrorRecordsMode, e);
        }
    }

    public ResponseEntity<List<Map<String, Object>>> executeAliasSingle(String aliasName, GatewayRequest request) {
        validateRequest(request);
        OperationConfig operationConfig = operationConfig(aliasName);
        OperationConfig.OperationDescriptor descriptor = operationConfig.getOperationDescriptor();

        if (descriptor.getType() == OperationConfig.OperationType.PREPARED_STATEMENT) {
            return getResponseFromQuerySingle(request, operationConfig);
        }
        return getResponseFromSP(request, operationConfig);
    }

    public ResponseEntity<List<Map<String, Object>>> getResponseFromQuerySingle(GatewayRequest request, OperationConfig operationConfig) {
        OperationConfig.OperationDescriptor descriptor = operationConfig.getOperationDescriptor();
        List<Map<String, Object>> result = executeDynamicQuery(descriptor.getSql(), request, descriptor.getInputParameters());
        log.info("ResponseEntity result: {}", result);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    public ResponseEntity<List<Map<String, Object>>> getResponseFromSP(GatewayRequest request, OperationConfig operationConfig) {
        OperationConfig.OperationDescriptor descriptor = operationConfig.getOperationDescriptor();
        String spName = descriptor.getDatabaseObjectName();
        Map<String, Object> outParams = executeStoreFuncWithDynamicParams(
                getPackagename(spName),
                getSpName(spName),
                descriptor.getInputParameters(),
                request,
                descriptor.getOutputParameters()
        );

        List<Map<String, Object>> out = new ArrayList<>();
        out.add(outParams);
        log.debug("ResponseEntity result: {}", out);
        return new ResponseEntity<>(out, HttpStatus.OK);
    }

    private OperationConfig operationConfig(String aliasName) {
        OperationConfig operationConfig = operationConfigMap.get(aliasName);
        if (operationConfig == null || operationConfig.getOperationDescriptor() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alias not found: " + aliasName);
        }
        return operationConfig;
    }

    private void validateRequests(List<GatewayRequest> requests) {
        if (requests == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body must be a list");
        }
        requests.forEach(this::validateRequest);
    }

    private void validateRequest(GatewayRequest request) {
        try {
            connectionName(request);
            params(request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    private String connectionName(GatewayRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Missing request body");
        }
        return request.requireConnectionName();
    }

    private Map<String, Object> params(GatewayRequest request) {
        return request.requireParams();
    }

    private Object[] buildJdbcParameters(Map<String, Object> params, List<OperationConfig.ParameterDescriptor> inputParameters) {
        if (inputParameters == null || inputParameters.isEmpty()) {
            return params.values().toArray();
        }
        return inputParameters.stream()
                .sorted(Comparator.comparing(param -> param.getPosition() == null ? Integer.MAX_VALUE : param.getPosition()))
                .map(param -> parameterValue(params, param))
                .toArray();
    }

    private Object parameterValue(Map<String, Object> params, OperationConfig.ParameterDescriptor parameterDescriptor) {
        if (params.containsKey(parameterDescriptor.getName())) {
            return params.get(parameterDescriptor.getName());
        }
        return params.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(parameterDescriptor.getName()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(parameterDescriptor.getDefaultValue());
    }

    private Map<String, Object> jdbcParamsForCall(Map<String, Object> params, List<OperationConfig.ParameterDescriptor> inputParameters) {
        Map<String, Object> jdbcParams = new HashMap<>();
        inputParameters.forEach(parameter -> jdbcParams.put(parameter.getName(), parameterValue(params, parameter)));
        return jdbcParams;
    }

    private OperationConfig.ParameterDescriptor resultParameter(List<OperationConfig.ParameterDescriptor> outputParameters) {
        return outputParameters.stream()
                .filter(this::isResultParameter)
                .findFirst()
                .orElse(null);
    }

    private boolean isResultParameter(OperationConfig.ParameterDescriptor parameter) {
        return parameter.getName() != null && "RESULT".equalsIgnoreCase(parameter.getName());
    }

    private String getSpName(String spName) {
        String[] parts = spName.split("\\.");
        return parts[1];
    }

    private String getPackagename(String spName) {
        String[] parts = spName.split("\\.");
        return parts[0];
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
            case "SYS_REFCURSOR":
                return Types.REF_CURSOR;
            default:
                throw new IllegalArgumentException("Unsupported JDBC type: " + jdbcTypeName);
        }
    }

    public static Class<?> convertSqlTypeToJavaClass(int sqlType) {
        switch (sqlType) {
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
            case Types.REF_CURSOR:
                return Object.class;
            default:
                return null;
        }
    }

    public static class RecordExecutionResult {
        private final GatewayRequest request;
        private final List<Map<String, Object>> results;
        private final Exception exception;

        private RecordExecutionResult(GatewayRequest request, List<Map<String, Object>> results, Exception exception) {
            this.request = request;
            this.results = results;
            this.exception = exception;
        }

        static RecordExecutionResult success(GatewayRequest request, List<Map<String, Object>> results) {
            return new RecordExecutionResult(request, results, null);
        }

        static RecordExecutionResult failure(GatewayRequest request, Exception exception) {
            return new RecordExecutionResult(request, List.of(), exception);
        }

        public GatewayRequest request() {
            return request;
        }

        public List<Map<String, Object>> results() {
            return results;
        }

        public Exception exception() {
            return exception;
        }

        public boolean success() {
            return exception == null;
        }
    }

    @PreDestroy
    public void destroy() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("ExecutorService did not terminate");
            }
        } catch (InterruptedException ie) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
