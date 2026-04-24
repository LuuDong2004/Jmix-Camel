package com.vn.jmixcamel.dto;

import java.util.List;

public class DbLookupConfig {
    private String type;
    private List<String> by;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getBy() {
        return by;
    }

    public void setBy(List<String> by) {
        this.by = by;
    }
}
