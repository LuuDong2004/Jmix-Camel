package com.vn.transform.spi;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of flow state + validated config that a plugin reads from during
 * execution. Plugins access flow variables by path (no direct map exposure).
 *
 * <p>Path syntax: {@code <scope>.<varName>[.<nested>]} where scope ∈ {input, output, private}.
 * Examples: {@code "output.userResp.email"}, {@code "private.token"}.
 */
public interface TransformContext {

    /**
     * Resolve a dotted path against the flow state snapshot.
     *
     * @param path dotted ref (e.g. "output.userResp.firstName")
     * @return value or {@code null} if any segment is missing
     */
    Object resolve(String path);

    /**
     * Convenience accessor for the source variable. Equivalent to
     * {@code resolve(node.source)}, returns {@code null} if no source set on the node.
     */
    Object source();

    /**
     * Node-level target path (from {@code node.data.target}), e.g. {@code "output.userClean"}.
     * Plugins should write their main result here:
     * {@code TransformResult.builder().write(ctx.targetPath(), value)…}.
     *
     * <p>Returned path is guaranteed to start with {@code "output."} or {@code "private."}.
     * Returns {@code null} if the node has no target configured — plugins may then fall back
     * to a default or fail with {@link TransformException.Code#BAD_CONFIG}.
     */
    String targetPath();

    /**
     * Validated, deep-immutable config map (already JSON-Schema-validated by the host
     * and {@code ${...}} placeholders interpolated against flow state).
     */
    Map<String, Object> config();

    /** Typed config helper. Returns {@code defaultValue} if missing or wrong type. */
    String configString(String key, String defaultValue);
    boolean configBool(String key, boolean defaultValue);
    int configInt(String key, int defaultValue);
    List<String> configStringList(String key);

    /** ID of the TRANSFORM node currently executing. Useful for log MDC. */
    String nodeId();

    /** ID of the overall flow execution. Stable across all nodes in one /execute call. */
    String flowExecutionId();

    /** Plugin-scoped logger (auto-tagged with pluginId / nodeId / flowExecutionId). */
    PluginLogger logger();

    /** Wall-clock deadline (epoch ms). Plugins should bail out cooperatively if exceeded. */
    long deadlineEpochMs();

    /**
     * Call inside long-running loops. Throws {@link TransformException} with code
     * {@code TIMEOUT} if the deadline has been reached.
     */
    void checkDeadline() throws TransformException;
}
