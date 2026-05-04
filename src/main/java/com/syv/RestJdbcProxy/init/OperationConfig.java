package com.syv.RestJdbcProxy.init;

import java.util.List;

public class OperationConfig {
    private OperationDescriptor operationDescriptor;

    public OperationDescriptor getOperationDescriptor() {
        return operationDescriptor;
    }

    public void setOperationDescriptor(OperationDescriptor operationDescriptor) {
        this.operationDescriptor = operationDescriptor;
    }

    public static class OperationDescriptor {
        private OperationType type;
        private String databaseObjectName;
        private String sql;
        private List<ParameterDescriptor> inputParameters = List.of();
        private List<ParameterDescriptor> outputParameters = List.of();

        public OperationType getType() {
            return type;
        }

        public void setType(OperationType type) {
            this.type = type;
        }

        public String getDatabaseObjectName() {
            return databaseObjectName;
        }

        public void setDatabaseObjectName(String databaseObjectName) {
            this.databaseObjectName = databaseObjectName;
        }

        public String getSql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }

        public List<ParameterDescriptor> getInputParameters() {
            return inputParameters == null ? List.of() : inputParameters;
        }

        public void setInputParameters(List<ParameterDescriptor> inputParameters) {
            this.inputParameters = inputParameters == null ? List.of() : inputParameters;
        }

        public List<ParameterDescriptor> getOutputParameters() {
            return outputParameters == null ? List.of() : outputParameters;
        }

        public void setOutputParameters(List<ParameterDescriptor> outputParameters) {
            this.outputParameters = outputParameters == null ? List.of() : outputParameters;
        }
    }

    public enum OperationType {
        PREPARED_STATEMENT,
        CALLABLE_STATEMENT
    }

    public static class ParameterDescriptor {
        private String name;
        private String jdbcType;
        private Integer position;
        private Object defaultValue;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getJdbcType() {
            return jdbcType;
        }

        public void setJdbcType(String jdbcType) {
            this.jdbcType = jdbcType;
        }

        public Integer getPosition() {
            return position;
        }

        public void setPosition(Integer position) {
            this.position = position;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
        }
    }
}
