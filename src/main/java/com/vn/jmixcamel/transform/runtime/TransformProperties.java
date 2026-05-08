package com.vn.jmixcamel.transform.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Runtime configuration for transform plugins. Bound from {@code application.properties}
 * keys under {@code transforms.*}.
 */
@ConfigurationProperties(prefix = "transforms")
public class TransformProperties {

    /** Absolute path to the directory PF4J scans for plugin JARs. */
    private String pluginsDir = "E:/Spring/Plugins";

    /** Default per-plugin execution timeout in milliseconds. */
    private long defaultTimeoutMs = 5000;

    /** Hard cap on serialized result.writes size (bytes). */
    private long maxWritesBytes = 1_048_576; // 1MB

    /** Bounded executor pool size for plugin invocations. */
    private int poolSize = 8;

    /** Allowlist — only plugin IDs in this list are exposed via the catalog API. */
    private List<String> enabledPlugins = List.of();

    // ─── Upload settings (transforms.upload.*) ─────────────────────────────────

    /** Hard cap on uploaded JAR size (bytes). Default 20MB. */
    private long uploadMaxBytes = 20L * 1024 * 1024;

    /** Dev convenience: when true, uploaded plugin id auto-passes the allowlist
     *  (so users don't need to edit application.properties for each new plugin). */
    private boolean uploadAutoEnable = true;

    public String getPluginsDir() { return pluginsDir; }
    public void setPluginsDir(String v) { this.pluginsDir = v; }

    public long getDefaultTimeoutMs() { return defaultTimeoutMs; }
    public void setDefaultTimeoutMs(long v) { this.defaultTimeoutMs = v; }

    public long getMaxWritesBytes() { return maxWritesBytes; }
    public void setMaxWritesBytes(long v) { this.maxWritesBytes = v; }

    public int getPoolSize() { return poolSize; }
    public void setPoolSize(int v) { this.poolSize = v; }

    public List<String> getEnabledPlugins() { return enabledPlugins; }
    public void setEnabledPlugins(List<String> v) { this.enabledPlugins = v; }

    public long getUploadMaxBytes() { return uploadMaxBytes; }
    public void setUploadMaxBytes(long v) { this.uploadMaxBytes = v; }

    public boolean isUploadAutoEnable() { return uploadAutoEnable; }
    public void setUploadAutoEnable(boolean v) { this.uploadAutoEnable = v; }
}
