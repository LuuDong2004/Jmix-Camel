package com.vn.jmixcamel.transform.runtime;

/** Stable error codes returned by the plugin upload endpoint. */
public class PluginUploadException extends RuntimeException {

    public enum Code {
        EMPTY_FILE,
        FILE_TOO_LARGE,
        NOT_A_JAR,
        INVALID_PLUGIN_JAR,
        PLUGIN_ALREADY_EXISTS,
        PLUGIN_LOAD_FAILED,
        PLUGIN_DISABLED,
        IO_ERROR
    }

    private final Code code;

    public PluginUploadException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public PluginUploadException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() { return code; }
}
