package com.syv.RestJdbcProxy.init;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AliasConfig {

    private Alias alias;

    public Alias getAlias() {
        return alias;
    }

    public void setAlias(Alias alias) {
        this.alias = alias;
    }

    public static class Alias {

        @JsonProperty("prepared-statements")
        private PreparedStatement preparedStatementAlias;

        @JsonProperty("callable-statements")
        private CallableStatements callableStatements;
        public CallableStatements getCallableStatements() {
            return callableStatements;
        }

        public void setCallableStatements(CallableStatements callableStatements) {
            this.callableStatements = callableStatements;
        }

        public PreparedStatement getPreparedStatementAlias() {
            return preparedStatementAlias;
        }

        public void setPreparedStatementAlias(PreparedStatement preparedStatementAlias) {
            this.preparedStatementAlias = preparedStatementAlias;
        }
    }

//    private CallableStatements callableStatements;
//
//    public CallableStatements getCallableStatements() {
//        return callableStatements;
//    }
//
//    public void setCallableStatements(CallableStatements callableStatements) {
//        this.callableStatements = callableStatements;
//    }

public static class PreparedStatement {

    @JsonProperty("sql-statement-to-prepare")
    private String sqlStatementToPrepare;

    @JsonProperty("in-param")
    private InParam inParam;

    public String getSqlStatementToPrepare() {
        return sqlStatementToPrepare;
    }

    public void setSqlStatementToPrepare(String sqlStatementToPrepare) {
        this.sqlStatementToPrepare = sqlStatementToPrepare;
    }

    public InParam getInParam() {
        return inParam;
    }

    public void setInParam(InParam inParam) {
        this.inParam = inParam;
    }
}
    public static class CallableStatements {
        @JsonProperty("db-sp-name")
        private String dbSpName;
        @JsonProperty("in-param")
        private InParam inParam;
        @JsonProperty("out-param")
        private OutParam outParam;

        public String getDbSpName() {
            return dbSpName;
        }

        public void setDbSpName(String dbSpName) {
            this.dbSpName = dbSpName;
        }

        public InParam getInParam() {
            return inParam;
        }

        public void setInParam(InParam inParam) {
            this.inParam = inParam;
        }

        public OutParam getOutParam() {
            return outParam;
        }

        public void setOutParam(OutParam outParam) {
            this.outParam = outParam;
        }
    }

    public static class InParam {
        private List<Param> param;

        public List<Param> getParam() {
            return param;
        }

        public void setParam(List<Param> param) {
            this.param = param;
        }
    }

    public static class OutParam {
        private List<Param> param;

        public List<Param> getParam() {
            return param;
        }

        public void setParam(List<Param> param) {
            this.param = param;
        }
    }

    public static class Param {
        @JsonProperty("jdbc-param-name")
        private String jdbcParamName;
        @JsonProperty("jdbc-param-type")
        private String jdbcParamType;
        @JsonProperty("jdbc-param-index")
        private int jdbcParamIndex;
        @JsonProperty("jdbc-param-default")
        private String jdbcParamDefault;

        public String getJdbcParamName() {
            return jdbcParamName;
        }

        public void setJdbcParamName(String jdbcParamName) {
            this.jdbcParamName = jdbcParamName;
        }

        public String getJdbcParamType() {
            return jdbcParamType;
        }

        public void setJdbcParamType(String jdbcParamType) {
            this.jdbcParamType = jdbcParamType;
        }

        public int getJdbcParamIndex() {
            return jdbcParamIndex;
        }

        public void setJdbcParamIndex(int jdbcParamIndex) {
            this.jdbcParamIndex = jdbcParamIndex;
        }

        public String getJdbcParamDefault() {
            return jdbcParamDefault;
        }

        public void setJdbcParamDefault(String jdbcParamDefault) {
            this.jdbcParamDefault = jdbcParamDefault;
        }
    }
}
