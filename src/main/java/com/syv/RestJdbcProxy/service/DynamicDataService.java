package com.syv.RestJdbcProxy.service;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DynamicDataService {
    private static final Logger log = LoggerFactory.getLogger(DynamicDataService.class);
    private JdbcTemplate jdbcTemplate = new JdbcTemplate();;


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
    public List<Map<String, Object>> executeDynamicQuery(String sqlQuery, Map<String, Object> inParams) {
        List<Object> parameters =  inParams.entrySet().stream().map(param -> param.getValue()).collect(Collectors.toList());
        return jdbcTemplate.queryForList(sqlQuery, parameters.toArray());
    }
    public Map<String, Object> executeDynamicQueryWithOutParams(String sqlQuery) {
        return jdbcTemplate.queryForMap(sqlQuery);
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
}
