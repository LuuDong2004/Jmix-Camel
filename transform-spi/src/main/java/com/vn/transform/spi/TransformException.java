package com.vn.transform.spi;

/**
 * Thrown by plugins to signal a recoverable transform failure. The host wraps any other
 * thrown {@link Throwable} (including {@link Error}) into a {@code TransformException}
 * with code {@code PLUGIN_ERROR} before failing the node.
 */
public class TransformException extends RuntimeException {

    /** Stable error code for client/log filtering. */
    public enum Code {
        BAD_CONFIG,
        BAD_INPUT,
        TIMEOUT,
        PLUGIN_ERROR
    }

    private final Code code;

    public TransformException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public TransformException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() { return code; }
}
