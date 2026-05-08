package com.vn.transform.spi.plugincall;

import org.pf4j.ExtensionPoint;

/**
 * Contract for an approved Java extension callable from a {@code PLUGIN_CALL} flow node.
 *
 * <p>One PF4J plugin JAR may expose multiple extensions, each identified by a stable
 * {@link #code()}. Examples:
 * <pre>
 *   plugin "bhxh-auth-plugin" exposes:
 *     - BHXH_SIGN_REQUEST
 *     - BHXH_DECRYPT_RESPONSE
 *
 *   plugin "checksum-plugin" exposes:
 *     - SHA256
 *     - MD5
 * </pre>
 *
 * <p>Unlike {@code TransformPlugin} (data mapping/normalization), this contract is meant
 * for approved business/integration logic that low-code can't express: cryptographic
 * signing, vendor SDKs, binary protocols, captcha solvers, complex token refresh.
 *
 * <p>Like {@code TransformPlugin}, plugins MUST NOT open files, sockets, spawn processes,
 * or mutate global state. If HTTP/DB is needed, use REST_CALL/DB_QUERY upstream.
 */
public interface FlowPluginExtension extends ExtensionPoint {

    /** Stable extension code referenced from flow node JSON. e.g. "BHXH_SIGN_REQUEST". */
    String code();

    /** Optional UI label. Defaults to {@link #code()}. */
    default String displayName() { return code(); }

    /** Short human description for the catalog UI. */
    default String description() { return ""; }

    /**
     * Run the extension. MUST be deterministic-ish, side-effect-free, and respect
     * {@link PluginExecutionContext#deadlineEpochMs()}.
     */
    PluginResult execute(PluginExecutionContext context) throws PluginCallException;
}
