package com.syv.RestJdbcProxy.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JsonPropertyOrder({"errors", "results"})
public class BatchExecutionResponse {
    private List<BatchErrorRecord> errors = new ArrayList<>();
    private List<Map<String, Object>> results = new ArrayList<>();

    public BatchExecutionResponse() {
    }

    public BatchExecutionResponse(List<BatchErrorRecord> errors, List<Map<String, Object>> results) {
        this.errors = errors;
        this.results = results;
    }

    public List<BatchErrorRecord> getErrors() {
        return errors;
    }

    public void setErrors(List<BatchErrorRecord> errors) {
        this.errors = errors;
    }

    public List<Map<String, Object>> getResults() {
        return results;
    }

    public void setResults(List<Map<String, Object>> results) {
        this.results = results;
    }
}
