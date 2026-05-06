package com.vn.jmixcamel.runner;

import com.vn.jmixcamel.service.ExtractCodeParser;
import com.vn.jmixcamel.service.ExtractCodeParser.ExprPart;
import com.vn.jmixcamel.service.ExtractCodeParser.ParsedLine;
import com.vn.jmixcamel.service.ResponseTemplateResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes an EXTRACT node:
 * <ol>
 *   <li>Parse the multi-line {@code code} field via {@link ExtractCodeParser}.</li>
 *   <li>Evaluate each line's RHS against the current scope.</li>
 *   <li>Write the value to the resolved {@code targetPath} ({@code output.X.Y…} or
 *       {@code private.X.Y…}). Nested keys are auto-created.</li>
 * </ol>
 */
@Component
public class ExtractRunner {

    private static final Logger log = LoggerFactory.getLogger(ExtractRunner.class);
    private final ResponseTemplateResolver templateResolver;

    public ExtractRunner(ResponseTemplateResolver templateResolver) {
        this.templateResolver = templateResolver;
    }

    public void run(Map<String, Object> data, Map<String, Object> scope) {
        String code = (String) data.get("code");
        String defaultTarget = (String) data.get("target");
        if (code == null || code.isBlank()) return;

        List<ParsedLine> lines = ExtractCodeParser.parse(code, defaultTarget);
        int wrote = 0;
        for (ParsedLine line : lines) {
            if (line.isComment) continue;
            if (line.error != null) {
                throw new IllegalArgumentException("Extract code: " + line.error);
            }
            Object value = evaluate(line.parts, scope);
            writeToScope(line.targetPath, value, scope);
            wrote++;
        }
        log.info("Extract wrote {} value(s); target='{}'", wrote, defaultTarget);
    }

    private Object evaluate(List<ExprPart> parts, Map<String, Object> scope) {
        if (parts == null || parts.isEmpty()) return null;
        if (parts.size() == 1) return resolvePart(parts.get(0), scope);

        // Multi-part: if all resolve to numbers → sum; otherwise string concat.
        Object[] values = new Object[parts.size()];
        boolean allNumber = true;
        for (int i = 0; i < parts.size(); i++) {
            values[i] = resolvePart(parts.get(i), scope);
            if (!(values[i] instanceof Number)) allNumber = false;
        }

        if (allNumber) {
            boolean allLong = true;
            double sum = 0;
            for (Object v : values) {
                Number n = (Number) v;
                sum += n.doubleValue();
                if (!(n instanceof Long || n instanceof Integer)) allLong = false;
            }
            return allLong ? (long) sum : sum;
        }

        StringBuilder sb = new StringBuilder();
        for (Object v : values) sb.append(v == null ? "null" : v.toString());
        return sb.toString();
    }

    private Object resolvePart(ExprPart p, Map<String, Object> scope) {
        return switch (p.kind) {
            case STRING -> p.value;
            case NUMBER -> p.value;
            case BOOL   -> p.value;
            case NULL   -> null;
            case REF    -> templateResolver.lookup((String) p.value, scope);
        };
    }

    @SuppressWarnings("unchecked")
    private void writeToScope(String targetPath, Object value, Map<String, Object> scope) {
        int dot = targetPath.indexOf('.');
        if (dot < 0) throw new IllegalStateException("Invalid target path: " + targetPath);
        String ns = targetPath.substring(0, dot);
        String rest = targetPath.substring(dot + 1);

        // 'private' is the FE-facing name; runtime uses the 'object' bucket aliased to 'private'.
        String beNs = "private".equals(ns) ? "object" : ns;
        Map<String, Object> bucket = (Map<String, Object>) scope.get(beNs);
        if (bucket == null) {
            bucket = new LinkedHashMap<>();
            scope.put(beNs, bucket);
            if ("private".equals(ns)) scope.put("private", bucket);
        }

        String[] segs = rest.split("\\.");
        Map<String, Object> current = bucket;
        for (int i = 0; i < segs.length - 1; i++) {
            Object next = current.get(segs[i]);
            if (!(next instanceof Map)) {
                Map<String, Object> nested = new LinkedHashMap<>();
                current.put(segs[i], nested);
                current = nested;
            } else {
                current = (Map<String, Object>) next;
            }
        }
        current.put(segs[segs.length - 1], value);
    }
}
