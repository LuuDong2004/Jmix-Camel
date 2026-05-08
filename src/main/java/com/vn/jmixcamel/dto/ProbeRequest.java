package com.vn.jmixcamel.dto;

import java.util.Map;

/**
 * Request body for /api/dynamic/probe — a one-shot execution of a single REST_CALL node
 * used by the designer UI to discover the response shape before defining Extract rules.
 */
public class ProbeRequest {
    /** REST_CALL node data: method, url, headers, bodyMode, bodyForm, body. */
    private Map<String, Object> nodeData;
    /** Current input variables (e.g. {"userId":"1","token":"..."}). */
    private Map<String, Object> input;
    /** Optional private/object scope variables. */
    private Map<String, Object> object;

    public Map<String, Object> getNodeData() { return nodeData; }
    public void setNodeData(Map<String, Object> nodeData) { this.nodeData = nodeData; }

    public Map<String, Object> getInput() { return input; }
    public void setInput(Map<String, Object> input) { this.input = input; }

    public Map<String, Object> getObject() { return object; }
    public void setObject(Map<String, Object> object) { this.object = object; }
}
