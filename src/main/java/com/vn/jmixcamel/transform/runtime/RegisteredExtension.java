package com.vn.jmixcamel.transform.runtime;

import com.vn.transform.spi.plugincall.FlowPluginExtension;

/**
 * One {@link FlowPluginExtension} registered in {@link PluginRegistry}, paired with the
 * PF4J plugin id and version it came from. Used by the catalog API and the
 * {@code PLUGIN_CALL} runner.
 */
public record RegisteredExtension(
        String pluginCode,
        String extensionCode,
        String version,
        String displayName,
        String description,
        FlowPluginExtension instance
) {
    public String key() { return pluginCode + ":" + extensionCode; }
}
