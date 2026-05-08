package com.vn.jmixcamel.transform.runtime;

import com.vn.transform.spi.TransformPlugin;
import com.vn.transform.spi.plugincall.FlowPluginExtension;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Wraps a PF4J {@link PluginManager} and tracks two families of extensions:
 * <ul>
 *   <li>{@link TransformPlugin} — keyed by descriptor id, used by TRANSFORM nodes.</li>
 *   <li>{@link FlowPluginExtension} — keyed by {@code (pluginCode, extensionCode)},
 *       used by PLUGIN_CALL nodes.</li>
 * </ul>
 *
 * <p>Plugin codes ({@code plugin.id} from {@code plugin.properties}) are the unit of
 * install/uninstall. A single JAR may contribute multiple {@code FlowPluginExtension}s.
 *
 * <p>If {@code transforms.enabled-plugins} is non-empty, only those ids are exposed.
 * Plugins outside the allowlist are still loaded by PF4J but lookups return empty.
 */
@Component
@EnableConfigurationProperties(TransformProperties.class)
public class PluginRegistry {

    private static final Logger log = LoggerFactory.getLogger(PluginRegistry.class);

    private final TransformProperties props;
    private PluginManager pluginManager;

    // descriptor.id → TransformPlugin (TRANSFORM nodes)
    private final ConcurrentMap<String, TransformPlugin> byId = new ConcurrentHashMap<>();
    // pluginCode → (extensionCode → RegisteredExtension)
    private final ConcurrentMap<String, ConcurrentMap<String, RegisteredExtension>> extByPlugin
            = new ConcurrentHashMap<>();

    private final Set<String> runtimeAllowed = ConcurrentHashMap.newKeySet();
    private final Object writeLock = new Object();

    public PluginRegistry(TransformProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void start() {
        Path dir = Paths.get(props.getPluginsDir());
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                log.info("Created plugins directory: {}", dir.toAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("Could not create plugins dir {}: {}", dir, e.getMessage());
        }

        pluginManager = new DefaultPluginManager(dir);
        pluginManager.loadPlugins();
        pluginManager.startPlugins();

        // Scan all started plugins for both kinds of extension.
        for (PluginWrapper w : pluginManager.getStartedPlugins()) {
            String pluginCode = w.getPluginId();
            registerTransformExtensions(pluginCode);
            registerFlowExtensions(pluginCode);
        }

        log.info("Plugin registry ready — {} transform-plugin(s), {} flow-extension(s) loaded from {}",
                byId.size(), totalExtensions(), dir.toAbsolutePath());
    }

    @PreDestroy
    public void stop() {
        for (TransformPlugin p : byId.values()) {
            try { p.close(); } catch (Exception ignored) {}
        }
        if (pluginManager != null) {
            pluginManager.stopPlugins();
            pluginManager.unloadPlugins();
        }
    }

    // ─── TRANSFORM lookup ──────────────────────────────────────────────────────

    public Optional<TransformPlugin> resolve(String id) {
        if (id == null) return Optional.empty();
        if (!isEnabled(id)) return Optional.empty();
        return Optional.ofNullable(byId.get(id));
    }

    public Collection<TransformPlugin> visiblePlugins() {
        if (props.getEnabledPlugins() == null || props.getEnabledPlugins().isEmpty()) {
            return byId.values();
        }
        return byId.values().stream().filter(p -> isEnabled(p.descriptor().id())).toList();
    }

    public boolean hasPlugin(String id) {
        return id != null && (byId.containsKey(id) || extByPlugin.containsKey(id));
    }

    // ─── PLUGIN_CALL lookup ────────────────────────────────────────────────────

    /** Lookup a flow extension by (pluginCode, extensionCode). */
    public Optional<RegisteredExtension> resolveExtension(String pluginCode, String extensionCode) {
        if (pluginCode == null || extensionCode == null) return Optional.empty();
        if (!isEnabled(pluginCode)) return Optional.empty();
        ConcurrentMap<String, RegisteredExtension> map = extByPlugin.get(pluginCode);
        if (map == null) return Optional.empty();
        return Optional.ofNullable(map.get(extensionCode));
    }

    /** Flat list of all visible extensions (for catalog API). */
    public List<RegisteredExtension> listExtensions() {
        List<RegisteredExtension> out = new ArrayList<>();
        for (var e : extByPlugin.entrySet()) {
            if (!isEnabled(e.getKey())) continue;
            out.addAll(e.getValue().values());
        }
        return out;
    }

    private int totalExtensions() {
        return extByPlugin.values().stream().mapToInt(java.util.Map::size).sum();
    }

    private boolean isEnabled(String id) {
        // Dev mode: when auto-enable is on, every loaded plugin is visible (no allowlist).
        if (props.isUploadAutoEnable()) return true;
        if (runtimeAllowed.contains(id)) return true;
        List<String> allow = props.getEnabledPlugins();
        return allow == null || allow.isEmpty() || allow.contains(id);
    }

    public void enableAtRuntime(String id) {
        if (id != null && !id.isBlank()) runtimeAllowed.add(id);
    }

    public void disableAtRuntime(String id) {
        if (id != null) runtimeAllowed.remove(id);
    }

    // ─── Runtime install / uninstall ───────────────────────────────────────────

    /**
     * Load + start a plugin from the given JAR. Scans for both {@link TransformPlugin}
     * and {@link FlowPluginExtension}. Throws if NEITHER is present.
     *
     * @return summary listing what was registered.
     */
    public InstallSummary installPlugin(Path jarPath) {
        synchronized (writeLock) {
            String pf4jId;
            try {
                pf4jId = pluginManager.loadPlugin(jarPath);
            } catch (Exception e) {
                throw new IllegalStateException("PF4J failed to load JAR: " + e.getMessage(), e);
            }
            if (pf4jId == null) {
                throw new IllegalStateException("PF4J returned null plugin id for " + jarPath);
            }
            if (byId.containsKey(pf4jId) || extByPlugin.containsKey(pf4jId)) {
                try { pluginManager.unloadPlugin(pf4jId); } catch (Exception ignored) {}
                throw new IllegalStateException("plugin id '" + pf4jId + "' is already registered");
            }
            try {
                PluginState state = pluginManager.startPlugin(pf4jId);
                if (state != PluginState.STARTED) {
                    throw new IllegalStateException("plugin " + pf4jId + " did not start (state=" + state + ")");
                }
                int transforms = registerTransformExtensions(pf4jId);
                int extensions = registerFlowExtensions(pf4jId);
                if (transforms == 0 && extensions == 0) {
                    throw new IllegalStateException("plugin " + pf4jId
                            + " has no @Extension TransformPlugin or FlowPluginExtension");
                }
                log.info("Installed plugin via upload: pluginCode={} transformExtensions={} flowExtensions={}",
                        pf4jId, transforms, extensions);

                PluginWrapper wrapper = pluginManager.getPlugin(pf4jId);
                String version = wrapper == null ? "unknown" : wrapper.getDescriptor().getVersion();
                List<TransformPlugin> tps = pluginManager.getExtensions(TransformPlugin.class, pf4jId);
                TransformPlugin transformPlugin = tps.isEmpty() ? null : tps.get(0);
                ConcurrentMap<String, RegisteredExtension> extMap = extByPlugin.get(pf4jId);
                List<RegisteredExtension> extList = extMap == null
                        ? List.of() : new ArrayList<>(extMap.values());
                return new InstallSummary(pf4jId, version, transformPlugin, extList);
            } catch (RuntimeException e) {
                try { pluginManager.unloadPlugin(pf4jId); } catch (Exception ignored) {}
                throw e;
            }
        }
    }

    /** Stop + unload a plugin and remove it from both registries. */
    public void uninstallPlugin(String pluginCode) {
        synchronized (writeLock) {
            TransformPlugin p = byId.remove(pluginCode);
            if (p != null) {
                try { p.close(); } catch (Exception ignored) {}
            }
            extByPlugin.remove(pluginCode);

            try { pluginManager.stopPlugin(pluginCode); } catch (Exception e) {
                log.warn("stopPlugin({}) failed: {}", pluginCode, e.getMessage());
            }
            try { pluginManager.unloadPlugin(pluginCode); } catch (Exception e) {
                log.warn("unloadPlugin({}) failed: {}", pluginCode, e.getMessage());
            }
            log.info("Uninstalled plugin: {}", pluginCode);
        }
    }

    // ─── Internal registration ─────────────────────────────────────────────────

    private int registerTransformExtensions(String pluginCode) {
        int count = 0;
        for (TransformPlugin p : pluginManager.getExtensions(TransformPlugin.class, pluginCode)) {
            try {
                String id = p.descriptor().id();
                if (byId.putIfAbsent(id, p) != null) {
                    log.warn("Duplicate transform-plugin id '{}' — keeping first", id);
                    continue;
                }
                log.info("Registered TransformPlugin: id={} version={} class={}",
                        id, p.descriptor().version(), p.getClass().getName());
                count++;
            } catch (Exception e) {
                log.error("Failed to register transform plugin {}: {}", p.getClass().getName(), e.getMessage(), e);
            }
        }
        return count;
    }

    private int registerFlowExtensions(String pluginCode) {
        ConcurrentMap<String, RegisteredExtension> map = extByPlugin.computeIfAbsent(
                pluginCode, k -> new ConcurrentHashMap<>());
        PluginWrapper wrapper = pluginManager.getPlugin(pluginCode);
        String version = wrapper == null ? "unknown" : wrapper.getDescriptor().getVersion();

        int count = 0;
        for (FlowPluginExtension ext : pluginManager.getExtensions(FlowPluginExtension.class, pluginCode)) {
            try {
                String code = ext.code();
                RegisteredExtension reg = new RegisteredExtension(
                        pluginCode, code, version, ext.displayName(), ext.description(), ext);
                if (map.putIfAbsent(code, reg) != null) {
                    log.warn("Duplicate flow-extension code '{}' in plugin '{}' — keeping first",
                            code, pluginCode);
                    continue;
                }
                log.info("Registered FlowPluginExtension: pluginCode={} extensionCode={} version={} class={}",
                        pluginCode, code, version, ext.getClass().getName());
                count++;
            } catch (Exception e) {
                log.error("Failed to register flow extension {}: {}",
                        ext.getClass().getName(), e.getMessage(), e);
            }
        }
        // Clean up if no extensions registered for this plugin
        if (map.isEmpty()) extByPlugin.remove(pluginCode);
        return count;
    }
}
