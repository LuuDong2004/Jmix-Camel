package com.vn.jmixcamel.dto;

import java.util.List;
import java.util.Map;

public class ExecutionConfig {
    private Map<String, Object> input;
    private Map<String, Object> output;
    private Map<String, Object> object;

    /** Graph-based execution (preferred). Order computed via topological sort of edges. */
    private List<FlowNode> nodes;
    private List<FlowEdge> edges;

    /** @deprecated Legacy flat config — kept for backward compat. Ignored when nodes is set. */
    @Deprecated
    private ApiConfig api;
    @Deprecated
    private Map<String, String> extract;
    @Deprecated
    private DbQueryConfig dbQuery;
    @Deprecated
    private Object response;

    public Map<String, Object> getInput() { return input; }
    public void setInput(Map<String, Object> input) { this.input = input; }

    public Map<String, Object> getOutput() { return output; }
    public void setOutput(Map<String, Object> output) { this.output = output; }

    public Map<String, Object> getObject() { return object; }
    public void setObject(Map<String, Object> object) { this.object = object; }

    public ApiConfig getApi() { return api; }
    public void setApi(ApiConfig api) { this.api = api; }

    public Map<String, String> getExtract() { return extract; }
    public void setExtract(Map<String, String> extract) { this.extract = extract; }

    public DbQueryConfig getDbQuery() { return dbQuery; }
    public void setDbQuery(DbQueryConfig dbQuery) { this.dbQuery = dbQuery; }

    public Object getResponse() { return response; }
    public void setResponse(Object response) { this.response = response; }

    public List<FlowNode> getNodes() { return nodes; }
    public void setNodes(List<FlowNode> nodes) { this.nodes = nodes; }

    public List<FlowEdge> getEdges() { return edges; }
    public void setEdges(List<FlowEdge> edges) { this.edges = edges; }
}
