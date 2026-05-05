package com.syv.RestJdbcProxy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"code", "message", "exceptionType"})
public class ErrorInfo {
    private String code;
    private String message;
    private String exceptionType;

    public ErrorInfo() {
    }

    public ErrorInfo(String code, String message, String exceptionType) {
        this.code = code;
        this.message = message;
        this.exceptionType = exceptionType;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }
}
