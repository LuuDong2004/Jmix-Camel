package com.vn.transform.spi.plugincall;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of a {@link FlowPluginExtension#execute} call.
 *
 * <p>Two distinct write channels:
 * <ul>
 *   <li>{@link #output()} — the "main" return value. Host writes it under
 *       {@code output.<outputKey>} where {@code outputKey} comes from the node JSON.</li>
 *   <li>{@link #variables()} — additional declarative writes (full paths under
 *       {@code output.*} or {@code private.*}). Validated by host.</li>
 * </ul>
 *
 * <p>Plugins MUST NOT mutate flow state directly — host applies all writes atomically.
 */
public final class PluginResult {

    private final Object output;
    private final Map<String, Object> variables;

    private PluginResult(Object output, Map<String, Object> variables) {
        this.output = output;
        this.variables = variables;
    }

    public Object output() { return output; }
    public Map<String, Object> variables() { return variables; }

    /** Most common case: just the main output. */
    public static PluginResult of(Object output) {
        return new PluginResult(output, Collections.emptyMap());
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Object output;
        private final Map<String, Object> variables = new LinkedHashMap<>();

        public Builder output(Object v) { this.output = v; return this; }
        public Builder write(String path, Object value) { variables.put(path, value); return this; }

        public PluginResult build() {
            return new PluginResult(output, Collections.unmodifiableMap(new LinkedHashMap<>(variables)));
        }
    }
}
