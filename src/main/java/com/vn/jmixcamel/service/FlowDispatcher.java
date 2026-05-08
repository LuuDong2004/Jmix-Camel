package com.vn.jmixcamel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.jmixcamel.dto.ExecutionConfig;
import com.vn.jmixcamel.dto.FlowEdge;
import com.vn.jmixcamel.dto.FlowNode;
import com.vn.jmixcamel.runner.DbQueryRunner;
import com.vn.jmixcamel.runner.ExtractRunner;
import com.vn.jmixcamel.runner.PluginCallRunner;
import com.vn.jmixcamel.runner.RestCallRunner;
import com.vn.jmixcamel.runner.TransformRunner;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Graph-based executor: walks the flow built on the canvas in topological order.
 * Replaces the old hard-coded REST → EXTRACT → DB → RESPONSE pipeline.
 *
 * Runtime scope is mutable — Extract writes to {@code output.X}, REST_CALL writes to
 * {@code body}, DB_QUERY writes to {@code dbResult}, RESPONSE produces the final return.
 */
@Component
public class FlowDispatcher implements Processor {

    private static final Logger log = LoggerFactory.getLogger(FlowDispatcher.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final RestCallRunner restCallRunner;
    private final ExtractRunner extractRunner;
    private final DbQueryRunner dbQueryRunner;
    private final ResponseTemplateResolver templateResolver;
    private final TransformRunner transformRunner;
    private final PluginCallRunner pluginCallRunner;

    public FlowDispatcher(RestCallRunner restCallRunner,
                          ExtractRunner extractRunner,
                          DbQueryRunner dbQueryRunner,
                          ResponseTemplateResolver templateResolver,
                          TransformRunner transformRunner,
                          PluginCallRunner pluginCallRunner) {
        this.restCallRunner = restCallRunner;
        this.extractRunner = extractRunner;
        this.dbQueryRunner = dbQueryRunner;
        this.templateResolver = templateResolver;
        this.transformRunner = transformRunner;
        this.pluginCallRunner = pluginCallRunner;
    }

    @Override
    public void process(Exchange exchange) {
        ExecutionConfig config = exchange.getProperty("execConfig", ExecutionConfig.class);
        if (config.getNodes() == null || config.getNodes().isEmpty()) {
            throw new IllegalArgumentException("config.nodes is empty — flow has no nodes to execute");
        }

        // Build evolving runtime scope.
        // 'private' aliases 'object' (same Map reference) so both ${object.X} and ${private.X} work.
        Map<String, Object> scope = new HashMap<>();
        Map<String, Object> objectBucket = copyMap(config.getObject());
        scope.put("input",     copyMap(config.getInput()));
        scope.put("output",    copyMap(config.getOutput()));
        scope.put("object",    objectBucket);
        scope.put("private",   objectBucket);
        scope.put("extracted", new LinkedHashMap<>());
        scope.put("body",      null);
        scope.put("dbResult",  null);

        List<FlowNode> sorted = topoSort(config.getNodes(), config.getEdges());
        log.info("Flow execution order: {}", sorted.stream().map(n -> n.getType() + "#" + n.getId()).toList());

        Object lastResponse = null;
        for (FlowNode node : sorted) {
            String type = node.getType();
            exchange.setProperty("stage", type + "_FAILED");
            log.info("→ Running {} ({})", type, node.getId());
            switch (type) {
                case "REST_CALL" -> {
                    String body = restCallRunner.run(node.getData(), scope, exchange);
                    Object parsed = parseJsonOrRaw(body);
                    // Legacy slot (kept for now while Extract still references body).
                    scope.put("body", parsed);
                    scope.put("bodyRaw", body);
                    // Store response in user-chosen output variable so downstream nodes
                    // can reference output.<varname> regardless of which REST_CALL produced it.
                    Object outputVar = node.getData() == null ? null : node.getData().get("output");
                    if (outputVar instanceof String s && !s.isBlank()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> outScope = (Map<String, Object>) scope.get("output");
                        outScope.put(s, parsed);
                    }
                }
                case "EXTRACT"   -> extractRunner.run(node.getData(), scope);
                case "TRANSFORM" -> transformRunner.run(node.getId(), node.getData(), scope);
                case "PLUGIN_CALL" -> pluginCallRunner.run(node.getId(), node.getData(), scope);
                case "DB_QUERY"  -> {
                    Object result = dbQueryRunner.run(node.getData(), scope);
                    String shape = (String) (node.getData() == null ? null : node.getData().get("resultShape"));
                    if ("single".equals(shape) && result instanceof List<?> list) {
                        result = list.isEmpty() ? null : list.get(0);
                    }
                    scope.put("dbResult", result);  // legacy slot
                    Object target = node.getData() == null ? null : node.getData().get("target");
                    if (target instanceof String s && !s.isBlank()) {
                        writeTargetVar(s, result, scope);
                    }
                }
                case "RESPONSE"  -> lastResponse = buildResponse(node.getData(), scope);
                default -> log.warn("Unknown node type: {}", type);
            }
        }

        exchange.setProperty("stage", "OK");
        Object body = lastResponse != null ? lastResponse : Map.of(
                "output",    scope.get("output"),
                "extracted", scope.get("extracted"),
                "dbResult",  scope.get("dbResult"));
        exchange.getIn().setBody(body);
    }

    @SuppressWarnings("unchecked")
    private Object buildResponse(Map<String, Object> data, Map<String, Object> scope) {
        Object template = data.get("template");
        if (template == null) return null;
        // FE sends template as Map<String,String>: {responseKey: expression}
        if (template instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            ((Map<String, Object>) m).forEach((k, v) -> out.put(k, templateResolver.resolve(v, scope)));
            return out;
        }
        return templateResolver.resolve(template, scope);
    }

    private Object parseJsonOrRaw(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            return mapper.readValue(body, Object.class);
        } catch (Exception e) {
            return body; // not JSON → treat as raw string
        }
    }

    private Map<String, Object> copyMap(Map<String, Object> src) {
        return src == null ? new LinkedHashMap<>() : new LinkedHashMap<>(src);
    }

    /**
     * Write a value to {@code <ns>.<path>} inside the runtime scope. Nested intermediate
     * maps are auto-created. {@code private} aliases the {@code object} bucket.
     */
    @SuppressWarnings("unchecked")
    private void writeTargetVar(String targetPath, Object value, Map<String, Object> scope) {
        int dot = targetPath.indexOf('.');
        if (dot < 0) return;
        String ns = targetPath.substring(0, dot);
        String rest = targetPath.substring(dot + 1);
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

    /**
     * Kahn's topological sort. Disconnected nodes come at the end in their original order.
     * Cycle → IllegalArgumentException.
     */
    private List<FlowNode> topoSort(List<FlowNode> nodes, List<FlowEdge> edges) {
        Map<String, FlowNode> byId = new LinkedHashMap<>();
        for (FlowNode n : nodes) byId.put(n.getId(), n);

        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Integer> indeg = new HashMap<>();
        for (FlowNode n : nodes) {
            adj.put(n.getId(), new ArrayList<>());
            indeg.put(n.getId(), 0);
        }
        if (edges != null) {
            for (FlowEdge e : edges) {
                if (!byId.containsKey(e.getSource()) || !byId.containsKey(e.getTarget())) continue;
                adj.get(e.getSource()).add(e.getTarget());
                indeg.merge(e.getTarget(), 1, Integer::sum);
            }
        }

        // Stable: process roots in original node order
        Deque<String> queue = new ArrayDeque<>();
        for (FlowNode n : nodes) {
            if (indeg.get(n.getId()) == 0) queue.add(n.getId());
        }

        List<FlowNode> sorted = new ArrayList<>(nodes.size());
        while (!queue.isEmpty()) {
            String id = queue.pollFirst();
            sorted.add(byId.get(id));
            for (String next : adj.get(id)) {
                int d = indeg.merge(next, -1, Integer::sum);
                if (d == 0) queue.add(next);
            }
        }
        if (sorted.size() != nodes.size()) {
            throw new IllegalArgumentException("Cycle detected in flow graph — cannot determine execution order");
        }
        return sorted;
    }
}
