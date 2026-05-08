package com.vn.jmixcamel.runner;

import com.vn.jmixcamel.transform.runtime.PluginCallExecutor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapts a PLUGIN_CALL FlowNode JSON to {@link PluginCallExecutor#run}.
 *
 * <p>Expected node JSON shape:
 * <pre>
 * {
 *   "id": "plugin_xxx",
 *   "type": "PLUGIN_CALL",
 *   "data": {
 *     "label": "Sign request",
 *     "pluginCode": "bhxh-auth-plugin",
 *     "extensionCode": "BHXH_SIGN_REQUEST",
 *     "version": "1.0.x",
 *     "inputMapping": { "payload": "${requestBody}", "credential": "${credential}" },
 *     "outputKey": "signedRequest",
 *     "timeoutMs": 3000
 *   }
 * }
 * </pre>
 */
@Component
public class PluginCallRunner {

    private final PluginCallExecutor executor;

    public PluginCallRunner(PluginCallExecutor executor) {
        this.executor = executor;
    }

    @SuppressWarnings("unchecked")
    public void run(String nodeId, Map<String, Object> data, Map<String, Object> scope) {
        String pluginCode = (String) data.get("pluginCode");
        String extensionCode = (String) data.get("extensionCode");
        String outputKey = (String) data.get("outputKey");

        Object inputObj = data.get("inputMapping");
        Map<String, Object> inputMapping = inputObj instanceof Map<?, ?> m
                ? (Map<String, Object>) m : new LinkedHashMap<>();

        long timeoutMs = 0;
        Object t = data.get("timeoutMs");
        if (t instanceof Number n) timeoutMs = n.longValue();
        else if (t instanceof String s && !s.isBlank()) {
            try { timeoutMs = Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }

        // tenantId / flowCode optional — pulled from node data if present, otherwise empty.
        String tenantId = (String) data.getOrDefault("tenantId", "");
        String flowCode = (String) data.getOrDefault("flowCode", "");

        executor.run(pluginCode, extensionCode, inputMapping, outputKey,
                timeoutMs, nodeId, tenantId, flowCode, scope);
    }

}
