package com.vn.transform.spi;

import org.pf4j.ExtensionPoint;

/**
 * Contract for a transform plugin loaded by the host via PF4J.
 *
 * <p>Plugins are pure transformations: read from {@link TransformContext} (immutable
 * snapshot of flow state + validated config), produce a {@link TransformResult}
 * containing declarative writes that the host applies atomically.
 *
 * <p><b>Plugins MUST NOT</b>: open files, sockets, spawn processes, mutate global state,
 * or access Spring/Camel internals. If HTTP/DB access is needed, use REST_CALL or
 * DB_QUERY nodes upstream/downstream of TRANSFORM.
 */
public interface TransformPlugin extends ExtensionPoint, AutoCloseable {

    /** Static metadata describing this plugin (id, version, displayName, etc.). */
    TransformDescriptor descriptor();

    /**
     * JSON Schema (draft-07) describing the shape of the {@code config} object the host
     * will pass to {@link #execute}. Returned as a raw JSON string so plugins are not
     * coupled to a specific JSON library version.
     */
    String configSchema();

    /**
     * Run the transformation. MUST be deterministic-ish, side-effect-free, and respect
     * {@link TransformContext#deadlineEpochMs()} (call {@link TransformContext#checkDeadline()}
     * periodically in long loops).
     */
    TransformResult execute(TransformContext ctx) throws TransformException;

    /** Hook for releasing plugin-owned resources (caches, compiled patterns, etc.). */
    @Override
    default void close() {}
}
