package com.vn.transform.spi.plugincall;

/** Thrown by FlowPluginExtension implementations to signal a recoverable failure. */
public class PluginCallException extends RuntimeException {

    public enum Code {
        BAD_CONFIG,
        BAD_INPUT,
        TIMEOUT,
        PLUGIN_ERROR,
        EXTENSION_NOT_FOUND
    }

    private final Code code;

    public PluginCallException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public PluginCallException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() { return code; }
}
