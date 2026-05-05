package com.syv.RestJdbcProxy.dto;

public enum BatchErrorCode {
    SQL_ERROR,
    BAD_REQUEST,
    ALIAS_NOT_FOUND,
    CONNECTION_NOT_FOUND,
    PARAMETER_MAPPING_ERROR,
    UNSUPPORTED_JDBC_TYPE,
    TIMEOUT,
    UNKNOWN_ERROR
}
