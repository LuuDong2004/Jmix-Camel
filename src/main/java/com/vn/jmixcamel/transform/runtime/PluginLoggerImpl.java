package com.vn.jmixcamel.transform.runtime;

import com.vn.transform.spi.PluginLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SLF4J-backed {@link PluginLogger}. Adds a {@code [pluginId/nodeId]} prefix to every
 * line so plugin output is greppable in host logs.
 */
final class PluginLoggerImpl implements PluginLogger {

    private static final Logger log = LoggerFactory.getLogger("transform.plugin");
    private final String prefix;

    PluginLoggerImpl(String pluginId, String nodeId, String flowExecutionId) {
        this.prefix = "[" + pluginId + "/" + nodeId + "/" + flowExecutionId + "] ";
    }

    @Override public void debug(String message, Object... args) { log.debug(prefix + message, args); }
    @Override public void info(String message, Object... args)  { log.info(prefix + message, args); }
    @Override public void warn(String message, Object... args)  { log.warn(prefix + message, args); }
    @Override public void error(String message, Object... args) { log.error(prefix + message, args); }
    @Override public void error(String message, Throwable cause) { log.error(prefix + message, cause); }
}
