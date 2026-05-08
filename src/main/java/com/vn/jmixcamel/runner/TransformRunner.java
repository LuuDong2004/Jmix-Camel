package com.vn.jmixcamel.runner;

import com.vn.jmixcamel.service.ResponseTemplateResolver;
import com.vn.jmixcamel.transform.runtime.TransformExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Executes a TRANSFORM node. Branches by {@code data.mode}:
 * <ul>
 *   <li>{@code "mapping"} — delegate to {@link ExtractRunner} (legacy DSL).</li>
 *   <li>{@code "plugin"} — call {@link TransformExecutor} with the plugin id + config.</li>
 * </ul>
 *
 * <p>Default mode is {@code "mapping"} so a TRANSFORM node missing the {@code mode} key
 * behaves like the legacy EXTRACT node.
 */
@Component
public class TransformRunner {

    private static final Logger log = LoggerFactory.getLogger(TransformRunner.class);

    private final ExtractRunner extractRunner;
    private final TransformExecutor transformExecutor;
    private final ResponseTemplateResolver resolver;

    public TransformRunner(ExtractRunner extractRunner,
                           TransformExecutor transformExecutor,
                           ResponseTemplateResolver resolver) {
        this.extractRunner = extractRunner;
        this.transformExecutor = transformExecutor;
        this.resolver = resolver;
    }

    public void run(String nodeId, Map<String, Object> data, Map<String, Object> scope) {
        String mode = (String) data.getOrDefault("mode", "mapping");
        switch (mode) {
            case "mapping" -> extractRunner.run(data, scope);
            case "plugin"  -> runPlugin(nodeId, data, scope);
            default -> throw new IllegalArgumentException(
                    "TRANSFORM node has unknown mode: '" + mode + "' (expected 'mapping' or 'plugin')");
        }
    }

    @SuppressWarnings("unchecked")
    private void runPlugin(String nodeId, Map<String, Object> data, Map<String, Object> scope) {
        Object pluginObj = data.get("plugin");
        if (!(pluginObj instanceof Map<?, ?> pluginMap)) {
            throw new IllegalArgumentException(
                    "TRANSFORM mode=plugin requires data.plugin object with id+config");
        }
        Map<String, Object> p = (Map<String, Object>) pluginMap;
        String pluginId = (String) p.get("id");
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("TRANSFORM mode=plugin requires data.plugin.id");
        }
        Object cfgObj = p.get("config");
        Map<String, Object> config = cfgObj instanceof Map<?, ?> m
                ? (Map<String, Object>) m : new LinkedHashMap<>();
        String sourcePath = (String) data.get("source");
        String targetPath = (String) data.get("target");

        // Diagnostic: resolve source NOW so we log exactly what plugin will see.
        Object resolvedSrc = (sourcePath == null || sourcePath.isBlank())
                ? null : resolver.lookup(sourcePath, scope);
        String srcType = resolvedSrc == null ? "null" : resolvedSrc.getClass().getSimpleName();
        String srcKeys = "";
        if (resolvedSrc instanceof Map<?, ?> m) {
            srcKeys = " keys=" + m.keySet();
        }
        log.info("→ TRANSFORM plugin '{}' (node {}) source={} (type={}{}) target={}",
                pluginId, nodeId, sourcePath, srcType, srcKeys, targetPath);

        if (sourcePath != null && !sourcePath.isBlank() && resolvedSrc == null) {
            throw new IllegalArgumentException(
                    "BAD_INPUT: source path '" + sourcePath + "' did not resolve to any value " +
                    "(scope keys: " + scope.keySet() + ")");
        }

        transformExecutor.run(pluginId, sourcePath, targetPath, config, nodeId, scope);
    }
}
