package com.vn.jmixcamel.transform.runtime;

import com.vn.transform.spi.TransformPlugin;

import java.util.List;

/**
 * Summary of a plugin install — returned by {@link PluginRegistry#installPlugin(java.nio.file.Path)}.
 *
 * <p>{@code transformPlugin} is non-null when the JAR contains an {@code @Extension
 * TransformPlugin}; {@code extensions} lists any {@code FlowPluginExtension}s found.
 * At least one of the two will be present (registry throws otherwise).
 */
public record InstallSummary(
        String pluginCode,
        String version,
        TransformPlugin transformPlugin,
        List<RegisteredExtension> extensions
) {
    public boolean hasTransform() { return transformPlugin != null; }
    public boolean hasExtensions() { return extensions != null && !extensions.isEmpty(); }
}
