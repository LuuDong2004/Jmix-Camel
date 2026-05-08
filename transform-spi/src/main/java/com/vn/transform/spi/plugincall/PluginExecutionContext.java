package com.vn.transform.spi.plugincall;

import com.vn.transform.spi.PluginLogger;

import java.util.Map;

/**
 * Immutable snapshot passed to {@link FlowPluginExtension#execute}.
 *
 * <p>Compared to {@code TransformContext}, this exposes:
 * <ul>
 *   <li>an explicit {@link #input()} map (already interpolated from flow variables)</li>
 *   <li>a read-only view of all flow variables for ad-hoc reads</li>
 *   <li>tenant/flow IDs for multi-tenant audit and routing</li>
 * </ul>
 */
public interface PluginExecutionContext {

    /** Multi-tenant identifier (empty in single-tenant deployments). */
    String tenantId();

    /** Flow code (the saved flow this execution belongs to). May be empty for ad-hoc runs. */
    String flowCode();

    /** ID of the PLUGIN_CALL node currently executing. */
    String nodeId();

    /** Stable id for the overall flow execution. */
    String flowExecutionId();

    /**
     * Input map built from {@code node.data.inputMapping} — keys are plugin parameter
     * names, values are already-resolved (placeholders expanded). Read-only.
     */
    Map<String, Object> input();

    /**
     * Read-only view of all flow variables grouped by scope.
     * Keys: {@code "input"}, {@code "output"}, {@code "private"}.
     * Plugins should normally use {@link #input()} or {@link #resolve(String)} —
     * this is escape hatch for advanced reads.
     */
    Map<String, Map<String, Object>> variables();

    /** Resolve a dotted path like {@code "output.userResp.email"} against flow state. */
    Object resolve(String path);

    /** Plugin-scoped logger (auto-tagged with pluginCode/extensionCode/nodeId). */
    PluginLogger logger();

    /** Wall-clock deadline (epoch ms). Plugin should bail out cooperatively. */
    long deadlineEpochMs();

    /** Throws {@link PluginCallException} with code TIMEOUT if deadline exceeded. */
    void checkTimeout() throws PluginCallException;
}
