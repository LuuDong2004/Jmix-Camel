package com.vn.jmixcamel.dto;

import java.util.Map;

public class FlowNode {
    private String id;
    private String type;          // REST_CALL | EXTRACT | DB_QUERY | RESPONSE
    private Map<String, Object> data;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}
