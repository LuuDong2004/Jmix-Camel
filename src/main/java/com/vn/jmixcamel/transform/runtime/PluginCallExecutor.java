package com.vn.jmixcamel.transform.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.jmixcamel.service.ResponseTemplateResolver;
import com.vn.transform.spi.PluginLogger;
import com.vn.transform.spi.plugincall.FlowPluginExtension;
import com.vn.transform.spi.plugincall.PluginCallException;
import com.vn.transform.spi.plugincall.PluginExecutionContext;
import com.vn.transform.spi.plugincall.PluginResult;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
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
 * Executes a single PLUGIN_CALL node end-to-end:
 * <ol>
 *   <li>Resolve {@code (pluginCode, extensionCode)} via {@link PluginRegistry}.</li>
 *   <li>Interpolate {@code inputMapping} values against the flow scope.</li>
 *   <li>Build immutable {@link PluginExecutionContext}.</li>
 *   <li>Submit to bounded pool with hard timeout (per-node {@code timeoutMs}).</li>
 *   <li>Validate {@code result.variables} write paths + size cap.</li>
 *   <li>Apply atomically: {@code result.output} → {@code output.<outputKey>};
 *       {@code result.variables} → fully-qualified paths.</li>
 * </ol>
 *
 * <p>Like {@code TransformExecutor}, every {@link Throwable} from the plugin is wrapped
 * in {@link PluginCallException} — host JVM is shielded from plugin Errors.
 */
@Component
public class PluginCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(PluginCallExecutor.class);
    private static final Pattern WRITE_PATH = Pattern.compile("^(output|private)\\.[a-zA-Z_][\\w.]*$");

    private final PluginRegistry registry;
    private final ConfigInterpolator interpolator;
    private final ResponseTemplateResolver resolver;
    private final TransformProperties props;
    private final ExecutorService pool;
    private final ObjectMapper mapper = new ObjectMapper();

    public PluginCallExecutor(PluginRegistry registry,
                              ConfigInterpolator interpolator,
                              ResponseTemplateResolver resolver,
                              TransformProperties props) {
        this.registry = registry;
        this.interpolator = interpolator;
        this.resolver = resolver;
        this.props = props;
        AtomicInteger counter = new AtomicInteger(0);
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "plugin-call-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.pool = Executors.newFixedThreadPool(props.getPoolSize(), tf);
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdownNow();
    }

    /** Run a PLUGIN_CALL node. */
    public void run(String pluginCode,
                    String extensionCode,
                    Map<String, Object> rawInputMapping,
                    String outputKey,
                    long timeoutMs,
                    String nodeId,
                    String tenantId,
                    String flowCode,
                    Map<String, Object> scope) {

        if (pluginCode == null || pluginCode.isBlank()) {
            throw new PluginCallException(PluginCallException.Code.BAD_CONFIG,
                    "PLUGIN_CALL requires data.pluginCode");
        }
        if (extensionCode == null || extensionCode.isBlank()) {
            throw new PluginCallException(PluginCallException.Code.BAD_CONFIG,
                    "PLUGIN_CALL requires data.extensionCode");
        }
        if (outputKey == null || outputKey.isBlank()) {
            throw new PluginCallException(PluginCallException.Code.BAD_CONFIG,
                    "PLUGIN_CALL requires data.outputKey (variable name to write under output.*)");
        }
        if (!outputKey.matches("[a-zA-Z_][\\w.]*")) {
            throw new PluginCallException(PluginCallException.Code.BAD_CONFIG,
                    "outputKey contains invalid characters: " + outputKey);
        }

        RegisteredExtension reg = registry.resolveExtension(pluginCode, extensionCode)
                .orElseThrow(() -> new PluginCallException(PluginCallException.Code.EXTENSION_NOT_FOUND,
                        "EXTENSION_NOT_FOUND: pluginCode=" + pluginCode
                                + " extensionCode=" + extensionCode));

        long effTimeout = timeoutMs > 0 ? timeoutMs : props.getDefaultTimeoutMs();
        long deadline = System.currentTimeMillis() + effTimeout;

        Map<String, Object> rawInput = rawInputMapping == null ? new LinkedHashMap<>() : rawInputMapping;
        Map<String, Object> interpolated = interpolator.interpolate(rawInput, scope);

        String flowExecId = String.valueOf(scope.computeIfAbsent("__flowExecId",
                k -> UUID.randomUUID().toString()));

        PluginLogger logger = new PluginLoggerImpl(
                pluginCode + "/" + extensionCode, nodeId, flowExecId);
        PluginExecutionContext ctx = new DefaultPluginExecutionContext(
                scope, resolver, interpolated,
                tenantId, flowCode, nodeId, flowExecId, deadline, logger);

        log.info("→ PLUGIN_CALL pluginCode={} extensionCode={} (node {}) inputKeys={} outputKey={} timeoutMs={}",
                pluginCode, extensionCode, nodeId, interpolated.keySet(), outputKey, effTimeout);

        FlowPluginExtension extension = reg.instance();
        PluginResult result;
        Future<PluginResult> f = pool.submit(() -> {
            try {
                return extension.execute(ctx);
            } catch (Throwable t) {
                throw new PluginCallException(PluginCallException.Code.PLUGIN_ERROR,
                        "extension " + reg.key() + " threw: " + t.getMessage(), t);
            }
        });
        try {
            result = f.get(effTimeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            throw new PluginCallException(PluginCallException.Code.TIMEOUT,
                    "extension " + reg.key() + " timed out after " + effTimeout + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PluginCallException(PluginCallException.Code.PLUGIN_ERROR,
                    "interrupted while running " + reg.key());
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof PluginCallException pce) throw pce;
            throw new PluginCallException(PluginCallException.Code.PLUGIN_ERROR,
                    "extension " + reg.key() + " failed: " + cause.getMessage(), cause);
        }

        if (result == null) {
            throw new PluginCallException(PluginCallException.Code.PLUGIN_ERROR,
                    "extension " + reg.key() + " returned null result");
        }

        applyOutput(reg.key(), outputKey, result, scope);
        log.info("PLUGIN_CALL {} → wrote output.{} + {} extra var(s)",
                reg.key(), outputKey,
                result.variables() == null ? 0 : result.variables().size());
    }

    private void applyOutput(String extKey, String outputKey, PluginResult result, Map<String, Object> scope) {
        // Build the merged write set
        Map<String, Object> writes = new LinkedHashMap<>();
        writes.put("output." + outputKey, result.output());
        if (result.variables() != null) writes.putAll(result.variables());

        // Validate paths
        for (String p : writes.keySet()) {
            if (p == null || !WRITE_PATH.matcher(p).matches()) {
                throw new PluginCallException(PluginCallException.Code.PLUGIN_ERROR,
                        "extension " + extKey + " produced invalid write path: '" + p + "'");
            }
        }

        // Cap size
        try {
            byte[] bytes = mapper.writeValueAsBytes(writes);
            if (bytes.length > props.getMaxWritesBytes()) {
                throw new PluginCallException(PluginCallException.Code.PLUGIN_ERROR,
                        "extension " + extKey + " writes exceed max size: "
                                + bytes.length + " > " + props.getMaxWritesBytes());
            }
        } catch (PluginCallException e) {
            throw e;
        } catch (Exception e) {
            throw new PluginCallException(PluginCallException.Code.PLUGIN_ERROR,
                    "extension " + extKey + " writes are not JSON-serializable: " + e.getMessage(), e);
        }

        // Atomic apply (snapshot + restore on failure)
        Map<String, Object> snap = snapshotScope(scope);
        try {
            for (var e : writes.entrySet()) writeTargetVar(e.getKey(), e.getValue(), scope);
        } catch (RuntimeException ex) {
            restoreScope(scope, snap);
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
