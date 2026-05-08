package com.vn.jmixcamel.transform.runtime;

import com.vn.jmixcamel.service.ResponseTemplateResolver;
import com.vn.transform.spi.PluginLogger;
import com.vn.transform.spi.TransformContext;
import com.vn.transform.spi.TransformException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Concrete {@link TransformContext}. Reads flow state via {@link ResponseTemplateResolver}
 * (so {@code "private.*"} → {@code "object.*"} aliasing matches the rest of the runtime).
 */
final class DefaultTransformContext implements TransformContext {

    private final Map<String, Object> scope;          // mutable scope (read-only access here)
    private final ResponseTemplateResolver resolver;
    private final String sourcePath;                  // node.data.source
    private final String targetPath;                  // node.data.target
    private final Map<String, Object> config;         // already validated + interpolated
    private final String nodeId;
    private final String flowExecutionId;
    private final String pluginId;
    private final long deadlineEpochMs;
    private final PluginLogger logger;

    DefaultTransformContext(Map<String, Object> scope,
                            ResponseTemplateResolver resolver,
                            String sourcePath,
                            String targetPath,
                            Map<String, Object> config,
                            String nodeId,
                            String flowExecutionId,
                            String pluginId,
                            long deadlineEpochMs) {
        this.scope = scope;
        this.resolver = resolver;
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.config = Collections.unmodifiableMap(config);
        this.nodeId = nodeId;
        this.flowExecutionId = flowExecutionId;
        this.pluginId = pluginId;
        this.deadlineEpochMs = deadlineEpochMs;
        this.logger = new PluginLoggerImpl(pluginId, nodeId, flowExecutionId);
    }

    @Override
    public Object resolve(String path) {
        if (path == null || path.isBlank()) return null;
        return resolver.lookup(path, scope);
    }

    @Override
    public Object source() {
        return sourcePath == null || sourcePath.isBlank() ? null : resolver.lookup(sourcePath, scope);
    }

    @Override
    public String targetPath() {
        return targetPath == null || targetPath.isBlank() ? null : targetPath;
    }

    @Override
    public Map<String, Object> config() { return config; }

    @Override
    public String configString(String key, String defaultValue) {
        Object v = config.get(key);
        return v instanceof String s ? s : defaultValue;
    }

    @Override
    public boolean configBool(String key, boolean defaultValue) {
        Object v = config.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return defaultValue;
    }

    @Override
    public int configInt(String key, int defaultValue) {
        Object v = config.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> configStringList(String key) {
        Object v = config.get(key);
        if (v instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }

    @Override public String nodeId() { return nodeId; }
    @Override public String flowExecutionId() { return flowExecutionId; }
    @Override public PluginLogger logger() { return logger; }
    @Override public long deadlineEpochMs() { return deadlineEpochMs; }

    @Override
    public void checkDeadline() throws TransformException {
        if (System.currentTimeMillis() > deadlineEpochMs) {
            throw new TransformException(TransformException.Code.TIMEOUT,
                    "plugin " + pluginId + " exceeded deadline");
        }
    }
}
