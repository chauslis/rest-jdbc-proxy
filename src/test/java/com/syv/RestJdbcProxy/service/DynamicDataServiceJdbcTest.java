package com.syv.RestJdbcProxy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syv.RestJdbcProxy.dto.BatchExecutionResponse;
import com.syv.RestJdbcProxy.dto.BatchErrorRecord;
import com.syv.RestJdbcProxy.config.DynamicDataSourceContextHolder;
import com.syv.RestJdbcProxy.dto.GatewayMetadata;
import com.syv.RestJdbcProxy.dto.GatewayRequest;
import com.syv.RestJdbcProxy.init.OperationConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicDataServiceJdbcTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DynamicDataService dynamicDataService = new DynamicDataService();

    @AfterEach
    void tearDown() {
        DynamicDataSourceContextHolder.clearDataSourceKey();
    }

    @Test
    void executeDynamicQueryUsesEnvelopeConnectionAndDoesNotMutateBusinessConnectionParam() {
        ReflectionTestUtils.setField(dynamicDataService, "jdbcTemplate", jdbcTemplate);
        Map<String, Object> params = new HashMap<>();
        params.put("connection", "business-value");
        params.put("id", 7);
        GatewayRequest request = gatewayRequest("DB2", params);
        List<OperationConfig.ParameterDescriptor> inputParameters = List.of(param("id", "BIGINT", 1));
        when(jdbcTemplate.queryForList(eq("select * from customer where id = ?"), any(Object[].class)))
                .thenReturn(List.of(new HashMap<>(Map.of("ID", 7))));

        List<Map<String, Object>> result = dynamicDataService.executeDynamicQuery(
                "select * from customer where id = ?",
                request,
                inputParameters
        );

        assertEquals(null, DynamicDataSourceContextHolder.getDataSourceKey());
        assertEquals(List.of(Map.of("ID", 7, "connectionName", "DB2")), result);
        assertEquals(Map.of("connection", "business-value", "id", 7), params);
        verify(jdbcTemplate).queryForList(eq("select * from customer where id = ?"), any(Object[].class));
    }

    @Test
    void executeDynamicQueryClearsConnectionWhenJdbcCallFails() {
        ReflectionTestUtils.setField(dynamicDataService, "jdbcTemplate", jdbcTemplate);
        GatewayRequest request = gatewayRequest("DB2", Map.of("id", 7));
        when(jdbcTemplate.queryForList(eq("select * from customer where id = ?"), any(Object[].class)))
                .thenThrow(new RuntimeException("query failed"));

        assertThrows(RuntimeException.class, () -> dynamicDataService.executeDynamicQuery(
                "select * from customer where id = ?",
                request,
                List.of(param("id", "BIGINT", 1))
        ));

        assertEquals(null, DynamicDataSourceContextHolder.getDataSourceKey());
    }

    @Test
    void distributeAndExecuteQuerySupportsMixedDatabaseConnectionsInOneRequest() throws Exception {
        ReflectionTestUtils.setField(dynamicDataService, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(dynamicDataService, "executorService", new SameThreadExecutorService());
        List<GatewayRequest> requests = List.of(
                gatewayRequest("DB1", Map.of("id", 1)),
                gatewayRequest("DB2", Map.of("id", 2)),
                gatewayRequest("DB1", Map.of("id", 3)),
                gatewayRequest("DB3", Map.of("id", 4))
        );
        when(jdbcTemplate.queryForList(eq("select * from customer where id = ?"), any(Object[].class)))
                .thenAnswer(invocation -> {
                    Object firstParameter = invocation.getArgument(1);
                    return List.of(new HashMap<>(Map.of("ID", firstParameter)));
                });

        List<DynamicDataService.RecordExecutionResult> result = dynamicDataService.distributeAndExecuteQuery(
                "select * from customer where id = ?",
                requests,
                List.of(param("id", "BIGINT", 1)),
                2
        );

        assertEquals(List.of(
                List.of(Map.of("ID", 1, "connectionName", "DB1")),
                List.of(Map.of("ID", 2, "connectionName", "DB2")),
                List.of(Map.of("ID", 3, "connectionName", "DB1")),
                List.of(Map.of("ID", 4, "connectionName", "DB3"))
        ), result.stream().map(DynamicDataService.RecordExecutionResult::results).toList());
        verify(jdbcTemplate, times(4)).queryForList(eq("select * from customer where id = ?"), any(Object[].class));
    }

    @Test
    void executeAliasBatchPreparedStatementReturnsErrorsFirstAndResults() {
        ReflectionTestUtils.setField(dynamicDataService, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(dynamicDataService, "executorService", new SameThreadExecutorService());
        ReflectionTestUtils.setField(dynamicDataService, "maxThreadsPerRequest", 10);
        dynamicDataService.operationConfigMap = Map.of("prepared", preparedOperation());
        when(jdbcTemplate.queryForList(eq("select * from customer where id = ?"), any(Object[].class)))
                .thenReturn(List.of(new HashMap<>(Map.of("ID", 1))));

        ResponseEntity<BatchExecutionResponse> response = dynamicDataService.executeAliasBatch(
                "prepared",
                List.of(gatewayRequest("DB1", Map.of("id", 1)))
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getErrors().isEmpty());
        assertEquals(List.of(Map.of("ID", 1, "connectionName", "DB1")), response.getBody().getResults());
    }

    @Test
    void executeAliasBatchPreparedStatementCapturesPartialFailure() {
        ReflectionTestUtils.setField(dynamicDataService, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(dynamicDataService, "executorService", new SameThreadExecutorService());
        ReflectionTestUtils.setField(dynamicDataService, "maxThreadsPerRequest", 10);
        dynamicDataService.operationConfigMap = Map.of("prepared", preparedOperation());
        when(jdbcTemplate.queryForList(eq("select * from customer where id = ?"), any(Object[].class)))
                .thenReturn(List.of(new HashMap<>(Map.of("ID", 1))))
                .thenThrow(new DataAccessResourceFailureException("query failed"))
                .thenReturn(List.of(new HashMap<>(Map.of("ID", 3))));

        ResponseEntity<BatchExecutionResponse> response = dynamicDataService.executeAliasBatch(
                "prepared",
                List.of(
                        gatewayRequest("DB1", Map.of("id", 1)),
                        gatewayRequest("DB2", Map.of("id", 2)),
                        gatewayRequest("DB3", Map.of("id", 3))
                )
        );

        BatchExecutionResponse body = response.getBody();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, body.getErrors().size());
        BatchErrorRecord errorRecord = body.getErrors().getFirst();
        assertEquals("DB2", errorRecord.getRjp().getConnectionName());
        assertEquals(Map.of("id", 2), errorRecord.getParams());
        assertEquals("FAILED", errorRecord.getStatus());
        assertEquals("SQL_ERROR", errorRecord.getError().getCode());
        assertEquals("query failed", errorRecord.getError().getMessage());
        assertNull(errorRecord.getError().getExceptionType());
        assertEquals(List.of(
                Map.of("ID", 1, "connectionName", "DB1"),
                Map.of("ID", 3, "connectionName", "DB3")
        ), body.getResults());
    }

    @Test
    void codeOnlyModeOmitsErrorMessageAndExceptionType() {
        ReflectionTestUtils.setField(dynamicDataService, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(dynamicDataService, "executorService", new SameThreadExecutorService());
        ReflectionTestUtils.setField(dynamicDataService, "batchErrorDetailsMode", "CODE_ONLY");
        ReflectionTestUtils.setField(dynamicDataService, "maxThreadsPerRequest", 10);
        dynamicDataService.operationConfigMap = Map.of("prepared", preparedOperation());
        when(jdbcTemplate.queryForList(eq("select * from customer where id = ?"), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("query failed"));

        BatchErrorRecord errorRecord = dynamicDataService.executeAliasBatch(
                "prepared",
                List.of(gatewayRequest("DB1", Map.of("id", 1)))
        ).getBody().getErrors().getFirst();

        assertEquals("SQL_ERROR", errorRecord.getError().getCode());
        assertNull(errorRecord.getError().getMessage());
        assertNull(errorRecord.getError().getExceptionType());
    }

    @Test
    void fullModeIncludesExceptionType() {
        ReflectionTestUtils.setField(dynamicDataService, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(dynamicDataService, "executorService", new SameThreadExecutorService());
        ReflectionTestUtils.setField(dynamicDataService, "batchErrorDetailsMode", "FULL");
        ReflectionTestUtils.setField(dynamicDataService, "maxThreadsPerRequest", 10);
        dynamicDataService.operationConfigMap = Map.of("prepared", preparedOperation());
        when(jdbcTemplate.queryForList(eq("select * from customer where id = ?"), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("query failed"));

        BatchErrorRecord errorRecord = dynamicDataService.executeAliasBatch(
                "prepared",
                List.of(gatewayRequest("DB1", Map.of("id", 1)))
        ).getBody().getErrors().getFirst();

        assertEquals("SQL_ERROR", errorRecord.getError().getCode());
        assertEquals("query failed", errorRecord.getError().getMessage());
        assertEquals("DataAccessResourceFailureException", errorRecord.getError().getExceptionType());
    }

    @Test
    void allRecordsModeIncludesSuccessAndFailureExecutionRecords() {
        ReflectionTestUtils.setField(dynamicDataService, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(dynamicDataService, "executorService", new SameThreadExecutorService());
        ReflectionTestUtils.setField(dynamicDataService, "batchErrorRecordsMode", "ALL");
        ReflectionTestUtils.setField(dynamicDataService, "maxThreadsPerRequest", 10);
        dynamicDataService.operationConfigMap = Map.of("prepared", preparedOperation());
        when(jdbcTemplate.queryForList(eq("select * from customer where id = ?"), any(Object[].class)))
                .thenReturn(List.of(new HashMap<>(Map.of("ID", 1))))
                .thenThrow(new DataAccessResourceFailureException("query failed"));

        BatchExecutionResponse response = dynamicDataService.executeAliasBatch(
                "prepared",
                List.of(gatewayRequest("DB1", Map.of("id", 1)), gatewayRequest("DB2", Map.of("id", 2)))
        ).getBody();

        assertEquals(2, response.getErrors().size());
        assertEquals("SUCCESS", response.getErrors().get(0).getStatus());
        assertNull(response.getErrors().get(0).getError());
        assertEquals("FAILED", response.getErrors().get(1).getStatus());
        assertEquals("SQL_ERROR", response.getErrors().get(1).getError().getCode());
    }

    @Test
    void batchResponseSerializesErrorsBeforeResults() throws Exception {
        BatchExecutionResponse response = new BatchExecutionResponse();
        response.setResults(List.of(Map.of("ID", 1)));

        String json = new ObjectMapper().writeValueAsString(response);

        assertTrue(json.indexOf("\"errors\"") < json.indexOf("\"results\""));
    }

    @Test
    void executeAliasBatchStoredProcedureReturnsOutputParamsWithSuccessRecords() {
        SimpleJdbcCall simpleJdbcCall = simpleJdbcCallMock();
        DynamicDataService service = serviceWithSimpleJdbcCall(simpleJdbcCall);
        ReflectionTestUtils.setField(service, "executorService", new SameThreadExecutorService());
        ReflectionTestUtils.setField(service, "maxThreadsPerRequest", 10);
        ReflectionTestUtils.setField(service, "batchErrorRecordsMode", "ALL");
        service.operationConfigMap = Map.of("callable", callableOperationWithOutParams());
        when(simpleJdbcCall.execute(any(Map.class))).thenReturn(new HashMap<>(Map.of("OUT1", "ok")));

        BatchExecutionResponse response = service.executeAliasBatch(
                "callable",
                List.of(gatewayRequest("DB1", Map.of("ID", 1)))
        ).getBody();

        assertEquals(1, response.getErrors().size());
        assertEquals("SUCCESS", response.getErrors().getFirst().getStatus());
        assertEquals(List.of(Map.of("OUT1", "ok")), response.getResults());
        verify(simpleJdbcCall).withProcedureName("test_proc");
        verify(simpleJdbcCall, never()).withFunctionName("test_proc");
    }

    @Test
    void executeAliasBatchStoredFunctionReturnsFunctionResult() {
        SimpleJdbcCall simpleJdbcCall = simpleJdbcCallMock();
        DynamicDataService service = serviceWithSimpleJdbcCall(simpleJdbcCall);
        ReflectionTestUtils.setField(service, "executorService", new SameThreadExecutorService());
        ReflectionTestUtils.setField(service, "maxThreadsPerRequest", 10);
        service.operationConfigMap = Map.of("function", callableFunctionOperation());
        when(simpleJdbcCall.executeFunction(eq(String.class), any(Map.class))).thenReturn("ok");

        BatchExecutionResponse response = service.executeAliasBatch(
                "function",
                List.of(gatewayRequest("DB1", Map.of("AN", "1")))
        ).getBody();

        assertTrue(response.getErrors().isEmpty());
        assertEquals(List.of(Map.of("result", "ok")), response.getResults());
        verify(simpleJdbcCall).withFunctionName("test_function");
        verify(simpleJdbcCall, never()).withProcedureName("test_function");
    }

    @Test
    void executeAliasBatchStoredProcedureCapturesDatabaseFailure() {
        SimpleJdbcCall simpleJdbcCall = simpleJdbcCallMock();
        DynamicDataService service = serviceWithSimpleJdbcCall(simpleJdbcCall);
        ReflectionTestUtils.setField(service, "executorService", new SameThreadExecutorService());
        ReflectionTestUtils.setField(service, "maxThreadsPerRequest", 10);
        service.operationConfigMap = Map.of("callable", callableOperationWithOutParams());
        when(simpleJdbcCall.execute(any(Map.class))).thenThrow(new DataAccessResourceFailureException("procedure failed"));

        BatchExecutionResponse response = service.executeAliasBatch(
                "callable",
                List.of(gatewayRequest("DB1", Map.of("ID", 1)))
        ).getBody();

        assertEquals(1, response.getErrors().size());
        assertEquals("FAILED", response.getErrors().getFirst().getStatus());
        assertEquals("SQL_ERROR", response.getErrors().getFirst().getError().getCode());
        assertEquals("procedure failed", response.getErrors().getFirst().getError().getMessage());
        assertTrue(response.getResults().isEmpty());
    }

    @Test
    void convertSqlTypeToJavaClassSupportsKnownTypesAndUnknownType() {
        assertEquals(Integer.class, DynamicDataService.convertSqlTypeToJavaClass(Types.INTEGER));
        assertEquals(Long.class, DynamicDataService.convertSqlTypeToJavaClass(Types.BIGINT));
        assertEquals(Short.class, DynamicDataService.convertSqlTypeToJavaClass(Types.SMALLINT));
        assertEquals(Float.class, DynamicDataService.convertSqlTypeToJavaClass(Types.FLOAT));
        assertEquals(Double.class, DynamicDataService.convertSqlTypeToJavaClass(Types.DOUBLE));
        assertEquals(Boolean.class, DynamicDataService.convertSqlTypeToJavaClass(Types.BOOLEAN));
        assertEquals(String.class, DynamicDataService.convertSqlTypeToJavaClass(Types.VARCHAR));
        assertEquals(java.sql.Timestamp.class, DynamicDataService.convertSqlTypeToJavaClass(Types.TIMESTAMP));
        assertEquals(java.sql.Blob.class, DynamicDataService.convertSqlTypeToJavaClass(Types.BLOB));
        assertEquals(java.sql.Clob.class, DynamicDataService.convertSqlTypeToJavaClass(Types.CLOB));
        assertEquals(java.sql.Array.class, DynamicDataService.convertSqlTypeToJavaClass(Types.ARRAY));
        assertEquals(java.sql.Struct.class, DynamicDataService.convertSqlTypeToJavaClass(Types.STRUCT));
        assertEquals(java.sql.Ref.class, DynamicDataService.convertSqlTypeToJavaClass(Types.REF));
        assertEquals(byte[].class, DynamicDataService.convertSqlTypeToJavaClass(Types.BINARY));
        assertEquals(Object.class, DynamicDataService.convertSqlTypeToJavaClass(Types.REF_CURSOR));
        assertNull(DynamicDataService.convertSqlTypeToJavaClass(Types.NULL));
    }

    @Test
    void convertsSupportedJdbcTypes() {
        assertEquals(Types.VARCHAR, DynamicDataService.convertStringToJdbcType("varchar"));
        assertEquals(Types.INTEGER, DynamicDataService.convertStringToJdbcType("INTEGER"));
        assertEquals(Types.BIGINT, DynamicDataService.convertStringToJdbcType("BIGINT"));
        assertEquals(Types.DOUBLE, DynamicDataService.convertStringToJdbcType("DOUBLE"));
        assertEquals(Types.REF_CURSOR, DynamicDataService.convertStringToJdbcType("SYS_REFCURSOR"));
    }

    @Test
    void rejectsUnsupportedJdbcType() {
        assertThrows(IllegalArgumentException.class, () -> DynamicDataService.convertStringToJdbcType("ARRAY"));
    }

    @Test
    void executeAliasBatchMissingAliasReturnsNotFound() {
        dynamicDataService.operationConfigMap = Map.of();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> dynamicDataService.executeAliasBatch("missing", List.of(gatewayRequest("DB1", Map.of("ID", 1))))
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void invalidRequestMissingMetadataReturnsBadRequest() {
        GatewayRequest request = new GatewayRequest();
        request.setParams(Map.of("ID", 1));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> dynamicDataService.executeAliasBatch("missing", List.of(request))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void invalidRequestMissingParamsReturnsBadRequest() {
        GatewayRequest request = new GatewayRequest();
        GatewayMetadata metadata = new GatewayMetadata();
        metadata.setConnectionName("DB1");
        request.setRjp(metadata);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> dynamicDataService.executeAliasBatch("missing", List.of(request))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void getResponseFromSpAllowsMissingOutParamDescriptor() {
        DynamicDataService service = spy(new DynamicDataService());
        OperationConfig operationConfig = callableOperationWithoutOutParams();
        GatewayRequest request = gatewayRequest("DB1", Map.of("ID", 1));
        doReturn(Map.of("status", "ok"))
                .when(service)
                .executeStoreFuncWithDynamicParams(
                        eq("test_pkg"),
                        eq("test_proc"),
                        eq(operationConfig.getOperationDescriptor().getInputParameters()),
                        eq(request),
                        eq(List.of())
                );

        ResponseEntity<List<Map<String, Object>>> response = service.getResponseFromSP(request, operationConfig);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(Map.of("status", "ok")), response.getBody());
    }

    private static GatewayRequest gatewayRequest(String connectionName, Map<String, Object> params) {
        GatewayMetadata metadata = new GatewayMetadata();
        metadata.setConnectionName(connectionName);
        GatewayRequest request = new GatewayRequest();
        request.setRjp(metadata);
        request.setParams(params);
        return request;
    }

    private static OperationConfig.ParameterDescriptor param(String name, String jdbcType, int position) {
        OperationConfig.ParameterDescriptor descriptor = new OperationConfig.ParameterDescriptor();
        descriptor.setName(name);
        descriptor.setJdbcType(jdbcType);
        descriptor.setPosition(position);
        return descriptor;
    }

    private static OperationConfig preparedOperation() {
        OperationConfig config = new OperationConfig();
        OperationConfig.OperationDescriptor operationDescriptor = new OperationConfig.OperationDescriptor();
        operationDescriptor.setType(OperationConfig.OperationType.PREPARED_STATEMENT);
        operationDescriptor.setSql("select * from customer where id = ?");
        operationDescriptor.setInputParameters(List.of(param("id", "BIGINT", 1)));
        config.setOperationDescriptor(operationDescriptor);
        return config;
    }

    private static OperationConfig callableOperationWithOutParams() {
        OperationConfig config = new OperationConfig();
        OperationConfig.OperationDescriptor operationDescriptor = new OperationConfig.OperationDescriptor();
        operationDescriptor.setType(OperationConfig.OperationType.CALLABLE_STATEMENT);
        operationDescriptor.setDatabaseObjectName("test_pkg.test_proc");
        operationDescriptor.setInputParameters(List.of(param("ID", "INTEGER", 1)));
        operationDescriptor.setOutputParameters(List.of(param("OUT1", "VARCHAR", 2)));
        config.setOperationDescriptor(operationDescriptor);
        return config;
    }

    private static OperationConfig callableFunctionOperation() {
        OperationConfig config = new OperationConfig();
        OperationConfig.OperationDescriptor operationDescriptor = new OperationConfig.OperationDescriptor();
        operationDescriptor.setType(OperationConfig.OperationType.CALLABLE_STATEMENT);
        operationDescriptor.setDatabaseObjectName("test_pkg.test_function");
        operationDescriptor.setInputParameters(List.of(param("AN", "VARCHAR", 1)));
        operationDescriptor.setOutputParameters(List.of(param("RESULT", "VARCHAR", 0)));
        config.setOperationDescriptor(operationDescriptor);
        return config;
    }

    private static SimpleJdbcCall simpleJdbcCallMock() {
        SimpleJdbcCall simpleJdbcCall = mock(SimpleJdbcCall.class);
        when(simpleJdbcCall.withCatalogName(any(String.class))).thenReturn(simpleJdbcCall);
        when(simpleJdbcCall.withProcedureName(any(String.class))).thenReturn(simpleJdbcCall);
        when(simpleJdbcCall.withFunctionName(any(String.class))).thenReturn(simpleJdbcCall);
        when(simpleJdbcCall.declareParameters(any())).thenReturn(simpleJdbcCall);
        return simpleJdbcCall;
    }

    private static DynamicDataService serviceWithSimpleJdbcCall(SimpleJdbcCall simpleJdbcCall) {
        return new DynamicDataService() {
            @Override
            protected SimpleJdbcCall createSimpleJdbcCall() {
                return simpleJdbcCall;
            }
        };
    }

    private static OperationConfig callableOperationWithoutOutParams() {
        OperationConfig config = new OperationConfig();
        OperationConfig.OperationDescriptor operationDescriptor = new OperationConfig.OperationDescriptor();
        operationDescriptor.setType(OperationConfig.OperationType.CALLABLE_STATEMENT);
        operationDescriptor.setDatabaseObjectName("test_pkg.test_proc");
        operationDescriptor.setInputParameters(List.of(param("ID", "INTEGER", 1)));
        config.setOperationDescriptor(operationDescriptor);
        return config;
    }
}
