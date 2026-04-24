package com.vn.jmixcamel.processor;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.jmixcamel.dto.ExecutionConfig;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DynamicExtractProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(DynamicExtractProcessor.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void process(Exchange exchange) throws Exception {
        ExecutionConfig config = exchange.getProperty("execConfig", ExecutionConfig.class);
        Map<String, String> paths = config.getExtract();

        Map<String, Object> extracted = new LinkedHashMap<>();
        if (paths == null || paths.isEmpty()) {
            exchange.setProperty("extracted", extracted);
            return;
        }

        String responseBody = exchange.getIn().getBody(String.class);
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("API response body is empty — cannot extract");
        }

        JsonNode root = mapper.readTree(responseBody);

        paths.forEach((field, path) -> {
            JsonNode node = root.at(toJsonPointer(path));
            if (node.isMissingNode() || node.isNull()) {
                extracted.put(field, null);
            } else if (node.isValueNode()) {
                extracted.put(field, node.asText());
            } else {
                extracted.put(field, mapper.convertValue(node, Object.class));
            }
        });

        log.info("Extracted fields: {}", extracted);
        exchange.setProperty("extracted", extracted);
    }

    private JsonPointer toJsonPointer(String jsonPath) {
        if (jsonPath == null || jsonPath.isBlank()) {
            return JsonPointer.empty();
        }
        String p = jsonPath.trim();
        if (p.startsWith("$")) {
            p = p.substring(1);
        }
        if (p.isEmpty()) {
            return JsonPointer.empty();
        }
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
