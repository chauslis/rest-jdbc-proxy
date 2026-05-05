package com.syv.RestJdbcProxy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"_rjp", "params", "status", "error"})
public class BatchErrorRecord {
    @JsonProperty("_rjp")
    private GatewayMetadata rjp;

    private Map<String, Object> params;
    private String status;
    private ErrorInfo error;

    public GatewayMetadata getRjp() {
        return rjp;
    }

    public void setRjp(GatewayMetadata rjp) {
        this.rjp = rjp;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ErrorInfo getError() {
        return error;
    }

    public void setError(ErrorInfo error) {
        this.error = error;
    }
}
