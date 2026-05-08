package com.vn.jmixcamel.transform.runtime;

import com.vn.jmixcamel.service.ResponseTemplateResolver;
import com.vn.transform.spi.PluginLogger;
import com.vn.transform.spi.plugincall.PluginCallException;
import com.vn.transform.spi.plugincall.PluginExecutionContext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Concrete {@link PluginExecutionContext}. Wraps the runtime scope without exposing
 * mutability — input + variables maps are deep-immutable views.
 */
final class DefaultPluginExecutionContext implements PluginExecutionContext {

    private final Map<String, Object> scope;
    private final ResponseTemplateResolver resolver;
    private final Map<String, Object> input;
    private final Map<String, Map<String, Object>> variables;
    private final String tenantId;
    private final String flowCode;
    private final String nodeId;
    private final String flowExecutionId;
    private final long deadlineEpochMs;
    private final PluginLogger logger;

    DefaultPluginExecutionContext(Map<String, Object> scope,
                                  ResponseTemplateResolver resolver,
                                  Map<String, Object> input,
                                  String tenantId,
                                  String flowCode,
                                  String nodeId,
                                  String flowExecutionId,
                                  long deadlineEpochMs,
                                  PluginLogger logger) {
        this.scope = scope;
        this.resolver = resolver;
        this.input = Collections.unmodifiableMap(new LinkedHashMap<>(input));
        this.variables = buildVariablesView(scope);
        this.tenantId = tenantId == null ? "" : tenantId;
        this.flowCode = flowCode == null ? "" : flowCode;
        this.nodeId = nodeId;
        this.flowExecutionId = flowExecutionId;
        this.deadlineEpochMs = deadlineEpochMs;
        this.logger = logger;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> buildVariablesView(Map<String, Object> scope) {
        Map<String, Map<String, Object>> v = new LinkedHashMap<>();
        v.put("input",   immutable((Map<String, Object>) scope.getOrDefault("input", Map.of())));
        v.put("output",  immutable((Map<String, Object>) scope.getOrDefault("output", Map.of())));
        // FE convention: "private" — runtime stores under "object". Expose "private" alias.
        v.put("private", immutable((Map<String, Object>) scope.getOrDefault("object", Map.of())));
        return Collections.unmodifiableMap(v);
    }

    private static Map<String, Object> immutable(Map<String, Object> m) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(m));
    }

    @Override public String tenantId() { return tenantId; }
    @Override public String flowCode() { return flowCode; }
    @Override public String nodeId() { return nodeId; }
    @Override public String flowExecutionId() { return flowExecutionId; }
    @Override public Map<String, Object> input() { return input; }
    @Override public Map<String, Map<String, Object>> variables() { return variables; }
    @Override public PluginLogger logger() { return logger; }
    @Override public long deadlineEpochMs() { return deadlineEpochMs; }

    @Override
    public Object resolve(String path) {
        if (path == null || path.isBlank()) return null;
        return resolver.lookup(path, scope);
    }

    @Override
    public void checkTimeout() throws PluginCallException {
        if (System.currentTimeMillis() > deadlineEpochMs) {
            throw new PluginCallException(PluginCallException.Code.TIMEOUT,
                    "extension exceeded deadline (node=" + nodeId + ")");
        }
    }
}
