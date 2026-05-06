package com.vn.jmixcamel.service;

import com.vn.jmixcamel.dto.ExecutionConfig;
import com.vn.jmixcamel.dto.FlowEdge;
import com.vn.jmixcamel.dto.FlowNode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Renders an ExecutionConfig graph into a Camel-DSL-flavored YAML/XML.
 * Output is for visualization only (Kaoto preview); it isn't fed back into the runtime.
 */
@Component
public class CamelDslEmitter {

    private static final Pattern HAS_PLACEHOLDER = Pattern.compile("\\$\\{[^}]+}");

    public String toXml(ExecutionConfig cfg) {
        StringBuilder sb = new StringBuilder();
        IdGen ids = new IdGen();
        sb.append("<camel xmlns=\"http://camel.apache.org/schema/spring\">\n");
        sb.append("    <route id=\"dynamic-execution\">\n");
        sb.append("        <from id=\"").append(ids.next("from")).append("\" uri=\"direct:dynamic\"/>\n");

        for (FlowNode n : topoSort(cfg)) {
            emitNodeXml(sb, ids, n);
        }

        sb.append("    </route>\n");
        sb.append("</camel>\n");
        return sb.toString();
    }

    public String toYaml(ExecutionConfig cfg) {
        StringBuilder sb = new StringBuilder();
        IdGen ids = new IdGen();
        sb.append("- route:\n");
        sb.append("    id: dynamic-execution\n");
        sb.append("    from:\n");
        sb.append("      id: ").append(ids.next("from")).append('\n');
        sb.append("      uri: direct:dynamic\n");
        sb.append("      steps:\n");

        for (FlowNode n : topoSort(cfg)) {
            emitNodeYaml(sb, ids, n);
        }

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void emitNodeXml(StringBuilder sb, IdGen ids, FlowNode n) {
        Map<String, Object> data = n.getData() == null ? Map.of() : n.getData();
        String label = (String) data.getOrDefault("label", n.getId());
        String type = n.getType();

        switch (type) {
            case "REST_CALL" -> {
                String method = String.valueOf(data.getOrDefault("method", "GET"));
                String url = String.valueOf(data.getOrDefault("url", ""));
                sb.append("        <log id=\"").append(ids.next("log"))
                  .append("\" message=\"").append(xmlEscape(label)).append("\"/>\n");
                sb.append("        <setHeader id=\"").append(ids.next("setHeader"))
                  .append("\" name=\"CamelHttpMethod\">\n            <constant>").append(xmlEscape(method))
                  .append("</constant>\n        </setHeader>\n");
                sb.append("        <toD id=\"").append(ids.next("toD"))
                  .append("\" uri=\"").append(xmlEscape(url)).append("\"/>\n");
            }
            case "EXTRACT" -> {
                String code = (String) data.getOrDefault("code", "");
                String oneLine = code.replaceAll("\\s+", " ").trim();
                sb.append("        <log id=\"").append(ids.next("log"))
                  .append("\" message=\"EXTRACT: ").append(xmlEscape(oneLine)).append("\"/>\n");
            }
            case "DB_QUERY" -> {
                sb.append("        <log id=\"").append(ids.next("log"))
                  .append("\" message=\"DB ").append(xmlEscape(buildDbDescriptor(data))).append("\"/>\n");
                sb.append("        <to id=\"").append(ids.next("to"))
                  .append("\" uri=\"bean:dbQueryRunner\"/>\n");
            }
            case "RESPONSE" -> {
                Object tmpl = data.get("template");
                int n_keys = tmpl instanceof Map<?, ?> m ? m.size() : 0;
                sb.append("        <log id=\"").append(ids.next("log"))
                  .append("\" message=\"RESPONSE (").append(n_keys).append(" keys)\"/>\n");
                sb.append("        <to id=\"").append(ids.next("to"))
                  .append("\" uri=\"bean:responseTemplateResolver\"/>\n");
            }
            default -> sb.append("        <log id=\"").append(ids.next("log"))
                  .append("\" message=\"unknown node type: ").append(xmlEscape(type)).append("\"/>\n");
        }
    }

    @SuppressWarnings("unchecked")
    private void emitNodeYaml(StringBuilder sb, IdGen ids, FlowNode n) {
        Map<String, Object> data = n.getData() == null ? Map.of() : n.getData();
        String label = (String) data.getOrDefault("label", n.getId());
        String type = n.getType();

        switch (type) {
            case "REST_CALL" -> {
                String method = String.valueOf(data.getOrDefault("method", "GET"));
                String url = String.valueOf(data.getOrDefault("url", ""));
                sb.append("        - log:\n");
                sb.append("            id: ").append(ids.next("log")).append('\n');
                sb.append("            message: ").append(yamlString(label)).append('\n');
                sb.append("        - setHeader:\n");
                sb.append("            id: ").append(ids.next("setHeader")).append('\n');
                sb.append("            name: CamelHttpMethod\n");
                sb.append("            constant: ").append(yamlString(method)).append('\n');
                sb.append("        - toD:\n");
                sb.append("            id: ").append(ids.next("toD")).append('\n');
                sb.append("            uri: ").append(yamlString(url)).append('\n');
            }
            case "EXTRACT" -> {
                String code = (String) data.getOrDefault("code", "");
                String oneLine = code.replaceAll("\\s+", " ").trim();
                sb.append("        - log:\n");
                sb.append("            id: ").append(ids.next("log")).append('\n');
                sb.append("            message: ").append(yamlString("EXTRACT: " + oneLine)).append('\n');
            }
            case "DB_QUERY" -> {
                sb.append("        - log:\n");
                sb.append("            id: ").append(ids.next("log")).append('\n');
                sb.append("            message: ").append(yamlString("DB " + buildDbDescriptor(data))).append('\n');
                sb.append("        - to:\n");
                sb.append("            id: ").append(ids.next("to")).append('\n');
                sb.append("            uri: bean:dbQueryRunner\n");
            }
            case "RESPONSE" -> {
                Object tmpl = data.get("template");
                int n_keys = tmpl instanceof Map<?, ?> m ? m.size() : 0;
                sb.append("        - log:\n");
                sb.append("            id: ").append(ids.next("log")).append('\n');
                sb.append("            message: ").append(yamlString("RESPONSE (" + n_keys + " keys)")).append('\n');
                sb.append("        - to:\n");
                sb.append("            id: ").append(ids.next("to")).append('\n');
                sb.append("            uri: bean:responseTemplateResolver\n");
            }
            default -> {
                sb.append("        - log:\n");
                sb.append("            id: ").append(ids.next("log")).append('\n');
                sb.append("            message: ").append(yamlString("unknown: " + type)).append('\n');
            }
        }
    }

    private String buildDbDescriptor(Map<String, Object> data) {
        Object sql = data.get("sql");
        if (sql instanceof String s && !s.isBlank()) {
            return "RAW SQL: " + s.replaceAll("\\s+", " ").trim();
        }
        StringBuilder s = new StringBuilder(String.valueOf(data.getOrDefault("entity", "")));
        Object filters = data.get("filters");
        if (filters instanceof List<?> list && !list.isEmpty()) {
            s.append(" WHERE ");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) s.append(" AND ");
                Object f = list.get(i);
                if (f instanceof Map<?, ?> fm) {
                    s.append(fm.get("field")).append(' ').append(fm.get("op")).append(' ').append(fm.get("value"));
                }
            }
        }
        Object orderBy = data.get("orderBy");
        if (orderBy instanceof String ob && !ob.isBlank()) {
            s.append(" ORDER BY ").append(ob);
            Object dir = data.get("orderDir");
            if (dir != null) s.append(' ').append(dir);
        }
        Object limit = data.get("limit");
        if (limit != null) s.append(" LIMIT ").append(limit);
        return s.toString();
    }

    private List<FlowNode> topoSort(ExecutionConfig cfg) {
        List<FlowNode> nodes = cfg.getNodes() == null ? List.of() : cfg.getNodes();
        if (nodes.isEmpty()) return nodes;
        Map<String, FlowNode> byId = new LinkedHashMap<>();
        for (FlowNode n : nodes) byId.put(n.getId(), n);
        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Integer> indeg = new HashMap<>();
        for (FlowNode n : nodes) { adj.put(n.getId(), new ArrayList<>()); indeg.put(n.getId(), 0); }
        if (cfg.getEdges() != null) {
            for (FlowEdge e : cfg.getEdges()) {
                if (!byId.containsKey(e.getSource()) || !byId.containsKey(e.getTarget())) continue;
                adj.get(e.getSource()).add(e.getTarget());
                indeg.merge(e.getTarget(), 1, Integer::sum);
            }
        }
        Deque<String> queue = new ArrayDeque<>();
        for (FlowNode n : nodes) if (indeg.get(n.getId()) == 0) queue.add(n.getId());
        List<FlowNode> sorted = new ArrayList<>(nodes.size());
        while (!queue.isEmpty()) {
            String id = queue.pollFirst();
            sorted.add(byId.get(id));
            for (String nx : adj.get(id)) {
                if (indeg.merge(nx, -1, Integer::sum) == 0) queue.add(nx);
            }
        }
        if (sorted.size() != nodes.size()) {
            // Cycle — append remainder so preview still shows something
            for (FlowNode n : nodes) if (!sorted.contains(n)) sorted.add(n);
        }
        return sorted;
    }

    private String xmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private String yamlString(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static final class IdGen {
        private final AtomicInteger seq = new AtomicInteger(1);
        String next(String prefix) { return prefix + "-" + seq.getAndIncrement(); }
    }
}
