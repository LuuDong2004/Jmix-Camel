package com.vn.jmixcamel.security;

import com.vn.jmixcamel.dto.ExecutionConfig;
import com.vn.jmixcamel.dto.FlowNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ConfigSecurityValidator {

    private static final Set<String> ALLOWED_URL_SCHEMES = Set.of("http", "https");
    private static final Set<String> BLOCKED_HTTP_METHODS = Set.of("TRACE", "CONNECT");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^}]+}");

    private static final Set<String> FORBIDDEN_SQL_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE",
            "TRUNCATE", "GRANT", "REVOKE", "EXEC", "EXECUTE", "MERGE", "CALL"
    );

    public void validate(ExecutionConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config is required");
        }
        List<FlowNode> nodes = config.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("config.nodes is empty — at least one node required");
        }
        for (FlowNode node : nodes) {
            validateNode(node);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateNode(FlowNode node) {
        Map<String, Object> data = node.getData() == null ? Map.of() : node.getData();
        String type = node.getType();
        if (type == null) {
            throw new IllegalArgumentException("Node " + node.getId() + " missing type");
        }
        switch (type) {
            case "REST_CALL" -> validateRestCall(node.getId(), data);
            case "DB_QUERY"  -> validateDbQuery(node.getId(), data);
            case "TRANSFORM" -> validateTransform(node.getId(), data);
            case "PLUGIN_CALL" -> validatePluginCall(node.getId(), data);
            case "EXTRACT", "RESPONSE" -> { /* no extra checks */ }
            default -> throw new IllegalArgumentException("Unknown node type: " + type);
        }
    }

    private void validatePluginCall(String nodeId, Map<String, Object> data) {
        String pluginCode = (String) data.get("pluginCode");
        if (pluginCode == null || pluginCode.isBlank()) {
            throw new IllegalArgumentException(nodeId + ": PLUGIN_CALL requires data.pluginCode");
        }
        String extensionCode = (String) data.get("extensionCode");
        if (extensionCode == null || extensionCode.isBlank()) {
            throw new IllegalArgumentException(nodeId + ": PLUGIN_CALL requires data.extensionCode");
        }
        String outputKey = (String) data.get("outputKey");
        if (outputKey == null || outputKey.isBlank()) {
            throw new IllegalArgumentException(nodeId + ": PLUGIN_CALL requires data.outputKey");
        }
        if (!outputKey.matches("[a-zA-Z_][\\w.]*")) {
            throw new IllegalArgumentException(nodeId + ": outputKey contains invalid characters: " + outputKey);
        }
        Object input = data.get("inputMapping");
        if (input != null && !(input instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(nodeId + ": inputMapping must be an object (got " + input.getClass().getSimpleName() + ")");
        }
    }

    private void validateTransform(String nodeId, Map<String, Object> data) {
        String mode = (String) data.getOrDefault("mode", "mapping");
        if (!"mapping".equals(mode) && !"plugin".equals(mode)) {
            throw new IllegalArgumentException(nodeId + ": TRANSFORM mode must be 'mapping' or 'plugin', got: " + mode);
        }
        if ("plugin".equals(mode)) {
            Object pluginObj = data.get("plugin");
            if (!(pluginObj instanceof Map<?, ?> pluginMap)) {
                throw new IllegalArgumentException(nodeId + ": TRANSFORM mode=plugin requires data.plugin object");
            }
            Object pid = pluginMap.get("id");
            if (!(pid instanceof String s) || s.isBlank()) {
                throw new IllegalArgumentException(nodeId + ": TRANSFORM mode=plugin requires data.plugin.id");
            }
            // config schema validation happens in TransformExecutor against plugin.configSchema()
        }
    }

    /** Public entry point for the /probe endpoint — validates a standalone REST_CALL data map. */
    public void validateRestCallData(String nodeId, Map<String, Object> data) {
        validateRestCall(nodeId, data == null ? Map.of() : data);
    }

    private void validateRestCall(String nodeId, Map<String, Object> data) {
        String method = (String) data.get("method");
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException(nodeId + ": method is required");
        }
        if (BLOCKED_HTTP_METHODS.contains(method.toUpperCase())) {
            throw new IllegalArgumentException(nodeId + ": HTTP method not allowed: " + method);
        }
        String url = (String) data.get("url");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(nodeId + ": url is required");
        }
        String urlForParse = PLACEHOLDER.matcher(url).replaceAll("x");
        URI uri;
        try {
            uri = new URI(urlForParse);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(nodeId + ": invalid URL — " + e.getMessage());
        }
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_URL_SCHEMES.contains(scheme.toLowerCase())) {
            throw new IllegalArgumentException(nodeId + ": only http/https schemes allowed. Got: " + scheme);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException(nodeId + ": URL must have a host");
        }
    }

    private void validateDbQuery(String nodeId, Map<String, Object> data) {
        String sql = (String) data.get("sql");
        boolean hasSql = sql != null && !sql.isBlank();
        String entity = (String) data.get("entity");
        boolean hasEntity = entity != null && !entity.isBlank();
        if (hasSql) {
            validateRawSql(nodeId, sql);
            return;
        }
        if (!hasEntity) {
            throw new IllegalArgumentException(nodeId + ": entity (or sql) is required");
        }
    }

    private void validateRawSql(String nodeId, String sql) {
        String stripped = sql.replaceAll("\\$\\{[^}]+}", "?").trim();
        String upper = stripped.toUpperCase();
        if (!(upper.startsWith("SELECT") || upper.startsWith("WITH"))) {
            throw new IllegalArgumentException(nodeId + ": only SELECT/WITH statements are allowed");
        }
        if (stripped.contains(";")) {
            throw new IllegalArgumentException(nodeId + ": multiple statements (;) not allowed");
        }
        if (stripped.contains("--") || stripped.contains("/*")) {
            throw new IllegalArgumentException(nodeId + ": SQL comments not allowed");
        }
        String guarded = " " + upper.replaceAll("[^A-Z0-9_]+", " ") + " ";
        for (String kw : FORBIDDEN_SQL_KEYWORDS) {
            if (guarded.contains(" " + kw + " ")) {
                throw new IllegalArgumentException(nodeId + ": keyword not allowed: " + kw);
            }
        }
    }
}
