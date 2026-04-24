package com.vn.jmixcamel.dto;

import java.util.Map;

public class ExecutionConfig {
    private Map<String, Object> input;
    private ApiConfig api;
    private Map<String, String> extract;
    private DbLookupConfig dbLookup;

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    public ApiConfig getApi() {
        return api;
    }

    public void setApi(ApiConfig api) {
        this.api = api;
    }

    public Map<String, String> getExtract() {
        return extract;
    }

    public void setExtract(Map<String, String> extract) {
        this.extract = extract;
    }

    public DbLookupConfig getDbLookup() {
        return dbLookup;
    }

    public void setDbLookup(DbLookupConfig dbLookup) {
        this.dbLookup = dbLookup;
    }
}
