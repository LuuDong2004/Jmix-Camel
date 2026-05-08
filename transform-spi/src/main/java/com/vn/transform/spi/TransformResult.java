package com.vn.transform.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Declarative result of a transform plugin. The host validates and applies the writes
 * atomically — plugins do NOT mutate flow state directly.
 *
 * <p>Write keys MUST match {@code ^(output|private)\.[a-zA-Z_][\w.]*$}. Keys outside
 * that pattern (especially {@code input.*}) are rejected by the host.
 */
public final class TransformResult {

    private final Map<String, Object> writes;
    private final List<String> warnings;
    private final Map<String, Number> metrics;

    private TransformResult(Map<String, Object> writes,
                            List<String> warnings,
                            Map<String, Number> metrics) {
        this.writes = writes;
        this.warnings = warnings;
        this.metrics = metrics;
    }

    public Map<String, Object> writes() { return writes; }
    public List<String> warnings() { return warnings; }
    public Map<String, Number> metrics() { return metrics; }

    public static TransformResult of(Map<String, Object> writes) {
        return new TransformResult(
                Collections.unmodifiableMap(new LinkedHashMap<>(writes)),
                Collections.emptyList(),
                Collections.emptyMap());
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final Map<String, Object> writes = new LinkedHashMap<>();
        private final List<String> warnings = new ArrayList<>();
        private final Map<String, Number> metrics = new LinkedHashMap<>();

        public Builder write(String path, Object value) { writes.put(path, value); return this; }
        public Builder warning(String msg) { warnings.add(msg); return this; }
        public Builder metric(String name, Number value) { metrics.put(name, value); return this; }

        public TransformResult build() {
            return new TransformResult(
                    Collections.unmodifiableMap(new LinkedHashMap<>(writes)),
                    Collections.unmodifiableList(new ArrayList<>(warnings)),
                    Collections.unmodifiableMap(new LinkedHashMap<>(metrics)));
        }
    }
}
