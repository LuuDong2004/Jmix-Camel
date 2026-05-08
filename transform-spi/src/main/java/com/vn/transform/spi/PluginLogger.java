package com.vn.transform.spi;

/**
 * Plugin-facing logger interface. Plugins should NOT use SLF4J / java.util.logging
 * directly — using this interface ensures all plugin log lines are tagged with
 * {@code pluginId}, {@code nodeId}, and {@code flowExecutionId}.
 */
public interface PluginLogger {
    void debug(String message, Object... args);
    void info(String message, Object... args);
    void warn(String message, Object... args);
    void error(String message, Object... args);
    void error(String message, Throwable cause);
}
