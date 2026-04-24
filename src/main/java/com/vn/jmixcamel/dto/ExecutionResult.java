package com.vn.jmixcamel.dto;

import java.util.Map;

public class ExecutionResult {
    private Map<String, Object> extracted;
    private Object dbResult;

    public ExecutionResult() {
    }

    public ExecutionResult(Map<String, Object> extracted, Object dbResult) {
        this.extracted = extracted;
        this.dbResult = dbResult;
    }

    public Map<String, Object> getExtracted() {
        return extracted;
    }

    public void setExtracted(Map<String, Object> extracted) {
        this.extracted = extracted;
    }

    public Object getDbResult() {
        return dbResult;
    }

    public void setDbResult(Object dbResult) {
        this.dbResult = dbResult;
    }
}
