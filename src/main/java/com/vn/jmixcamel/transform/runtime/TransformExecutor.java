package com.vn.jmixcamel.transform.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.jmixcamel.service.ResponseTemplateResolver;
import com.vn.transform.spi.TransformContext;
import com.vn.transform.spi.TransformException;
import com.vn.transform.spi.TransformPlugin;
import com.vn.transform.spi.TransformResult;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Orchestrates a single plugin invocation:
 * <ol>
 *   <li>resolve plugin via {@link PluginRegistry}</li>
 *   <li>validate config via {@link ConfigValidator} (against plugin's own schema)</li>
 *   <li>interpolate {@code ${...}} in config via {@link ConfigInterpolator}</li>
 *   <li>build immutable {@link TransformContext}</li>
 *   <li>execute on bounded pool with hard timeout</li>
 *   <li>validate result writes (path regex + size cap)</li>
 *   <li>apply writes atomically into the flow scope</li>
 * </ol>
 *
 * <p>Plugins are NOT trusted: any thrown {@link Throwable} (including {@link Error}) is
 * caught and wrapped, never propagated up to crash the JVM.
 */
@Component
public class TransformExecutor {

    private static final Logger log = LoggerFactory.getLogger(TransformExecutor.class);

    /** Allowed output paths. */
    private static final Pattern WRITE_PATH = Pattern.compile("^(output|private)\\.[a-zA-Z_][\\w.]*$");

    private final PluginRegistry registry;
    private final ConfigValidator validator;
    private final ConfigInterpolator interpolator;
    private final ResponseTemplateResolver resolver;
    private final TransformProperties props;
    private final ExecutorService pool;
    private final ObjectMapper mapper = new ObjectMapper();

    public TransformExecutor(PluginRegistry registry,
                             ConfigValidator validator,
                             ConfigInterpolator interpolator,
                             ResponseTemplateResolver resolver,
                             TransformProperties props) {
        this.registry = registry;
        this.validator = validator;
        this.interpolator = interpolator;
        this.resolver = resolver;
        this.props = props;
        AtomicInteger counter = new AtomicInteger(0);
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "transform-plugin-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.pool = Executors.newFixedThreadPool(props.getPoolSize(), tf);
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdownNow();
    }

    /**
     * Run a TRANSFORM plugin node.
     *
     * @param pluginId  plugin id from node.data.plugin.id
     * @param sourcePath value of node.data.source (may be null)
     * @param targetPath value of node.data.target (may be null) — exposed via {@code ctx.targetPath()}
     * @param rawConfig value of node.data.plugin.config (may be null)
     * @param nodeId id of the TRANSFORM node (for logs)
     * @param scope mutable flow scope (writes are applied into this map)
     */
    public TransformResult run(String pluginId,
                               String sourcePath,
                               String targetPath,
                               Map<String, Object> rawConfig,
                               String nodeId,
                               Map<String, Object> scope) {

        TransformPlugin plugin = registry.resolve(pluginId)
                .orElseThrow(() -> new TransformException(TransformException.Code.BAD_CONFIG,
                        "PLUGIN_NOT_FOUND: id=" + pluginId));

        String cacheKey = pluginId + "@" + plugin.descriptor().version();

        Map<String, Object> interpolated = interpolator.interpolate(
                rawConfig == null ? new LinkedHashMap<>() : rawConfig, scope);

        validator.validate(cacheKey, plugin.configSchema(), interpolated);

        long timeoutMs = props.getDefaultTimeoutMs();
        long deadline = System.currentTimeMillis() + timeoutMs;
        String flowExecId = String.valueOf(scope.computeIfAbsent("__flowExecId",
                k -> UUID.randomUUID().toString()));

        TransformContext ctx = new DefaultTransformContext(
                scope, resolver, sourcePath, targetPath, interpolated,
                nodeId, flowExecId, pluginId, deadline);

        TransformResult result;
        Future<TransformResult> f = pool.submit(() -> {
            try {
                return plugin.execute(ctx);
            } catch (Throwable t) { // catch Error too — never let plugin crash host
                throw new TransformException(TransformException.Code.PLUGIN_ERROR,
                        "plugin " + pluginId + " threw: " + t.getMessage(), t);
            }
        });
        try {
            result = f.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            throw new TransformException(TransformException.Code.TIMEOUT,
                    "plugin " + pluginId + " timed out after " + timeoutMs + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransformException(TransformException.Code.PLUGIN_ERROR,
                    "interrupted while running plugin " + pluginId);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof TransformException te) throw te;
            throw new TransformException(TransformException.Code.PLUGIN_ERROR,
                    "plugin " + pluginId + " failed: " + cause.getMessage(), cause);
        }

        if (result == null) {
            throw new TransformException(TransformException.Code.PLUGIN_ERROR,
                    "plugin " + pluginId + " returned null result");
        }

        validateAndApply(plugin.descriptor().id(), result, scope);

        if (result.warnings() != null && !result.warnings().isEmpty()) {
            for (String w : result.warnings()) log.warn("[plugin {}] {}", pluginId, w);
        }
        log.info("plugin {} produced {} write(s)", pluginId, result.writes().size());
        return result;
    }

    private void validateAndApply(String pluginId, TransformResult result, Map<String, Object> scope) {
        Map<String, Object> writes = result.writes();
        if (writes == null || writes.isEmpty()) return;

        // 1. validate paths
        for (String path : writes.keySet()) {
            if (path == null || !WRITE_PATH.matcher(path).matches()) {
                throw new TransformException(TransformException.Code.PLUGIN_ERROR,
                        "plugin " + pluginId + " produced invalid write path: '" + path + "'");
            }
        }

        // 2. cap size
        try {
            byte[] bytes = mapper.writeValueAsBytes(writes);
            if (bytes.length > props.getMaxWritesBytes()) {
                throw new TransformException(TransformException.Code.PLUGIN_ERROR,
                        "plugin " + pluginId + " writes exceed max size: "
                                + bytes.length + " > " + props.getMaxWritesBytes());
            }
        } catch (TransformException e) {
            throw e;
        } catch (Exception e) {
            throw new TransformException(TransformException.Code.PLUGIN_ERROR,
                    "plugin " + pluginId + " writes are not JSON-serializable: " + e.getMessage(), e);
        }

        // 3. atomic apply (all-or-nothing)
        Map<String, Object> snapshot = snapshotScope(scope);
        try {
            for (Map.Entry<String, Object> e : writes.entrySet()) {
                writeTargetVar(e.getKey(), e.getValue(), scope);
            }
        } catch (RuntimeException ex) {
            restoreScope(scope, snapshot);
            throw ex;
        }
    }

    @SuppressWarnings("unchecked")
    private void writeTargetVar(String targetPath, Object value, Map<String, Object> scope) {
        int dot = targetPath.indexOf('.');
        String ns = targetPath.substring(0, dot);
        String rest = targetPath.substring(dot + 1);
        String beNs = "private".equals(ns) ? "object" : ns;

        Map<String, Object> bucket = (Map<String, Object>) scope.get(beNs);
        if (bucket == null) {
            bucket = new LinkedHashMap<>();
            scope.put(beNs, bucket);
            if ("private".equals(ns)) scope.put("private", bucket);
        }

        String[] segs = rest.split("\\.");
        Map<String, Object> current = bucket;
        for (int i = 0; i < segs.length - 1; i++) {
            Object next = current.get(segs[i]);
            if (!(next instanceof Map)) {
                Map<String, Object> nested = new LinkedHashMap<>();
                current.put(segs[i], nested);
                current = nested;
            } else {
                current = (Map<String, Object>) next;
            }
        }
        current.put(segs[segs.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshotScope(Map<String, Object> scope) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("output", new LinkedHashMap<>((Map<String, Object>) scope.getOrDefault("output", Map.of())));
        snap.put("object", new LinkedHashMap<>((Map<String, Object>) scope.getOrDefault("object", Map.of())));
        return snap;
    }

    @SuppressWarnings("unchecked")
    private void restoreScope(Map<String, Object> scope, Map<String, Object> snapshot) {
        ((Map<String, Object>) scope.get("output")).clear();
        ((Map<String, Object>) scope.get("output")).putAll((Map<String, Object>) snapshot.get("output"));
        ((Map<String, Object>) scope.get("object")).clear();
        ((Map<String, Object>) scope.get("object")).putAll((Map<String, Object>) snapshot.get("object"));
    }
}
