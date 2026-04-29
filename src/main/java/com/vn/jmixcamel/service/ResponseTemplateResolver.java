package com.vn.jmixcamel.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a user-defined template by substituting ${path} references.
 * Scope is a namespace map, e.g.
 *   { "input": {...}, "extracted": {...}, "dbResult": {...}, "dbWrite": {...} }
 *
 * - Whole-string placeholder ${path} returns the typed value (Map/List/etc.)
 * - Multi-token strings perform interpolation.
 */
@Component
public class ResponseTemplateResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern WHOLE_PLACEHOLDER = Pattern.compile("^\\s*\\$\\{([^}]+)}\\s*$");

    public Object resolve(Object template, Map<String, Object> scope) {
        if (template == null) return null;
        if (template instanceof String s) return resolveString(s, scope);
        if (template instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), resolve(v, scope)));
            return out;
        }
        if (template instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) out.add(resolve(item, scope));
            return out;
        }
        return template;
    }

    private Object resolveString(String s, Map<String, Object> scope) {
        Matcher whole = WHOLE_PLACEHOLDER.matcher(s);
        if (whole.matches()) {
            return lookup(whole.group(1).trim(), scope);
        }
        Matcher m = PLACEHOLDER.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object v = lookup(m.group(1).trim(), scope);
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? "" : v.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private Object lookup(String path, Map<String, Object> scope) {
        if (path == null || path.isBlank()) return null;
        String[] parts = path.split("\\.");
        if (parts.length == 0) return null;
        Object current = scope.get(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (current == null) return null;
            if (current instanceof Map<?, ?> map) {
                current = map.get(parts[i]);
            } else {
                return null;
            }
        }
        return current;
    }
}
