package com.vn.jmixcamel.transform.runtime;

import com.vn.jmixcamel.service.ResponseTemplateResolver;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks a config tree and replaces {@code ${path}} placeholders against the flow scope
 * before handing the config to a plugin. Reuses {@link ResponseTemplateResolver} so the
 * substitution semantics match the rest of the runtime.
 */
@Component
public class ConfigInterpolator {

    private final ResponseTemplateResolver resolver;

    public ConfigInterpolator(ResponseTemplateResolver resolver) {
        this.resolver = resolver;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> interpolate(Map<String, Object> config, Map<String, Object> scope) {
        if (config == null) return new LinkedHashMap<>();
        return (Map<String, Object>) walk(config, scope);
    }

    private Object walk(Object node, Map<String, Object> scope) {
        if (node instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), walk(v, scope)));
            return out;
        }
        if (node instanceof List<?> list) {
            List<Object> out = new java.util.ArrayList<>(list.size());
            for (Object item : list) out.add(walk(item, scope));
            return out;
        }
        if (node instanceof String s) {
            return resolver.resolve(s, scope);
        }
        return node;
    }
}
