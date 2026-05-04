package com.syv.RestJdbcProxy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class GatewayRequest {
    @JsonProperty("_rjp")
    private GatewayMetadata rjp;

    private Map<String, Object> params;

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

    public String requireConnectionName() {
        if (rjp == null || rjp.getConnectionName() == null || rjp.getConnectionName().isBlank()) {
            throw new IllegalArgumentException("Missing _rjp.connectionName");
        }
        return rjp.getConnectionName();
    }

    public Map<String, Object> requireParams() {
        if (params == null) {
            throw new IllegalArgumentException("Missing params");
        }
        return params;
    }
}
