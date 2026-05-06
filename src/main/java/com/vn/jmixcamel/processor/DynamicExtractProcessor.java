package com.vn.jmixcamel.processor;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.jmixcamel.dto.ExecutionConfig;
import com.vn.jmixcamel.service.ResponseTemplateResolver;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DynamicExtractProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(DynamicExtractProcessor.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ResponseTemplateResolver templateResolver;

    public DynamicExtractProcessor(ResponseTemplateResolver templateResolver) {
        this.templateResolver = templateResolver;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        ExecutionConfig config = exchange.getProperty("execConfig", ExecutionConfig.class);
        Map<String, String> rules = config.getExtract();

        Map<String, Object> extracted = new LinkedHashMap<>();
        if (rules == null || rules.isEmpty()) {
            exchange.setProperty("extracted", extracted);
            return;
        }

        String responseBody = exchange.getIn().getBody(String.class);
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("API response body is empty — cannot extract");
        }

        JsonNode root = mapper.readTree(responseBody);
        Object bodyAsObject = mapper.convertValue(root, Object.class);

        // Build evolving scope so later rules can reference earlier `extracted.X`
        Map<String, Object> scope = new HashMap<>();
        scope.put("input",     config.getInput()  == null ? Map.of() : config.getInput());
        scope.put("output",    config.getOutput() == null ? Map.of() : config.getOutput());
        scope.put("object",    config.getObject() == null ? Map.of() : config.getObject());
        scope.put("body",      bodyAsObject);
        scope.put("extracted", extracted);

        rules.forEach((alias, expr) -> {
            Object value = evaluate(expr, root, scope);
            extracted.put(alias, value);
        });

        log.info("Extracted fields: {}", extracted);
        exchange.setProperty("extracted", extracted);
    }

    /**
     * Smart evaluator:
     *   - Contains "${...}" → Camel-Simple template, resolved via ResponseTemplateResolver
     *     (supports concat, mixed text + multiple var refs)
     *   - Otherwise → JSONPath at root (legacy behavior, supports $., body. prefix)
     */
    private Object evaluate(String expr, JsonNode root, Map<String, Object> scope) {
        if (expr == null) return null;
        String trimmed = expr.trim();
        if (trimmed.isEmpty()) return null;

        if (trimmed.contains("${") && trimmed.contains("}")) {
            return templateResolver.resolve(trimmed, scope);
        }

        // JSONPath mode — strip optional prefixes
        String path = trimmed;
        if (path.startsWith("$.")) path = path.substring(2);
        else if (path.startsWith("$")) path = path.substring(1);
        if (path.startsWith("body.")) path = path.substring(5);
        else if (path.equals("body")) path = "";

        if (path.isEmpty()) {
            return mapper.convertValue(root, Object.class);
        }

        JsonNode node = root.at(toJsonPointer(path));
        if (node.isMissingNode() || node.isNull()) return null;
        if (node.isValueNode()) return node.asText();
        return mapper.convertValue(node, Object.class);
    }

    private JsonPointer toJsonPointer(String jsonPath) {
        if (jsonPath == null || jsonPath.isBlank()) {
            return JsonPointer.empty();
        }
        String p = jsonPath.trim();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < p.length()) {
            char c = p.charAt(i);
            if (c == '.') {
                sb.append('/');
                i++;
            } else if (c == '[') {
                int end = p.indexOf(']', i);
                if (end < 0) {
                    throw new IllegalArgumentException("Unclosed '[' in path: " + jsonPath);
                }
                String idx = p.substring(i + 1, end).trim();
                if (idx.startsWith("'") && idx.endsWith("'")) {
                    idx = idx.substring(1, idx.length() - 1);
                } else if (idx.startsWith("\"") && idx.endsWith("\"")) {
                    idx = idx.substring(1, idx.length() - 1);
                }
                sb.append('/').append(idx);
                i = end + 1;
            } else {
                sb.append(c);
                i++;
            }
        }
        String pointer = sb.toString();
        if (!pointer.startsWith("/")) {
            pointer = "/" + pointer;
        }
        return JsonPointer.compile(pointer);
    }
}
