package com.vn.jmixcamel.service;

import com.vn.jmixcamel.dto.ApiConfig;
import com.vn.jmixcamel.dto.DbQueryConfig;
import com.vn.jmixcamel.dto.ExecutionConfig;
import com.vn.jmixcamel.dto.QueryFilter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Emits a Camel route DSL representation of an ExecutionConfig in formats Kaoto can render
 * (XML Spring schema or YAML DSL). The emitted route is a visualization of the same flow
 * the runtime executes via DynamicExecutionService — the runtime does not parse this DSL.
 *
 *   from(direct:dynamic)
 *     -> setHeader(input.*)
 *     -> setHeader(api.headers.*)
 *     -> setBody(api.body)?
 *     -> toD(api.url)
 *     -> setProperty(extracted.*)+jsonpath
 *     -> to(bean:dynamicDbQueryProcessor)?
 *     -> to(bean:responseTemplateResolver)?
 */
@Component
public class CamelDslEmitter {

    private static final Pattern HAS_PLACEHOLDER = Pattern.compile("\\$\\{[^}]+}");

    public String toXml(ExecutionConfig cfg) {
        StringBuilder sb = new StringBuilder();
        IdGen ids = new IdGen();

        sb.append("<camel xmlns=\"http://camel.apache.org/schema/spring\">\n");
        sb.append("    <route id=\"dynamic-execution\">\n");
        sb.append("        <from id=\"").append(ids.next("from"))
          .append("\" uri=\"direct:dynamic\"/>\n");

        if (cfg.getInput() != null) {
            for (Map.Entry<String, Object> e : cfg.getInput().entrySet()) {
                emitSetHeaderXml(sb, ids, e.getKey(), valueToString(e.getValue()), false);
            }
        }

        ApiConfig api = cfg.getApi();
        if (api != null) {
            if (api.getHeaders() != null) {
                for (Map.Entry<String, String> e : api.getHeaders().entrySet()) {
                    emitSetHeaderXml(sb, ids, e.getKey(), e.getValue(), true);
                }
            }
            if (api.getBody() != null && !valueToString(api.getBody()).isBlank()) {
                String body = valueToString(api.getBody());
                sb.append("        <setBody id=\"").append(ids.next("setBody")).append("\">\n");
                sb.append("            <").append(exprTag(body)).append('>')
                  .append(xmlEscape(body))
                  .append("</").append(exprTag(body)).append(">\n");
                sb.append("        </setBody>\n");
            }
            if (api.getUrl() != null && !api.getUrl().isBlank()) {
                String method = api.getMethod() == null ? "GET" : api.getMethod();
                sb.append("        <setHeader id=\"").append(ids.next("setHeader"))
                  .append("\" name=\"CamelHttpMethod\">\n")
                  .append("            <constant>").append(xmlEscape(method))
                  .append("</constant>\n        </setHeader>\n");
                sb.append("        <toD id=\"").append(ids.next("toD"))
                  .append("\" uri=\"").append(xmlEscape(api.getUrl())).append("\"/>\n");
            }
        }

        if (cfg.getExtract() != null) {
            for (Map.Entry<String, String> e : cfg.getExtract().entrySet()) {
                sb.append("        <setProperty id=\"").append(ids.next("setProperty"))
                  .append("\" name=\"extracted.").append(xmlEscape(e.getKey())).append("\">\n");
                sb.append("            <jsonpath>").append(xmlEscape(e.getValue()))
                  .append("</jsonpath>\n");
                sb.append("        </setProperty>\n");
            }
        }

        DbQueryConfig db = cfg.getDbQuery();
        if (db != null && db.getEntity() != null && !db.getEntity().isBlank()) {
            sb.append("        <log id=\"").append(ids.next("log"))
              .append("\" message=\"DB query ").append(xmlEscape(buildDbDescriptor(db))).append("\"/>\n");
            sb.append("        <to id=\"").append(ids.next("to"))
              .append("\" uri=\"bean:dynamicDbQueryProcessor\"/>\n");
        }

        if (cfg.getResponse() != null) {
            sb.append("        <to id=\"").append(ids.next("to"))
              .append("\" uri=\"bean:responseTemplateResolver\"/>\n");
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

        if (cfg.getInput() != null) {
            for (Map.Entry<String, Object> e : cfg.getInput().entrySet()) {
                emitSetHeaderYaml(sb, ids, e.getKey(), valueToString(e.getValue()), false);
            }
        }

        ApiConfig api = cfg.getApi();
        if (api != null) {
            if (api.getHeaders() != null) {
                for (Map.Entry<String, String> e : api.getHeaders().entrySet()) {
                    emitSetHeaderYaml(sb, ids, e.getKey(), e.getValue(), true);
                }
            }
            if (api.getBody() != null && !valueToString(api.getBody()).isBlank()) {
                String body = valueToString(api.getBody());
                sb.append("        - setBody:\n");
                sb.append("            id: ").append(ids.next("setBody")).append('\n');
                sb.append("            ").append(exprTag(body)).append(": ")
                  .append(yamlString(body)).append('\n');
            }
            if (api.getUrl() != null && !api.getUrl().isBlank()) {
                String method = api.getMethod() == null ? "GET" : api.getMethod();
                sb.append("        - setHeader:\n");
                sb.append("            id: ").append(ids.next("setHeader")).append('\n');
                sb.append("            name: CamelHttpMethod\n");
                sb.append("            constant: ").append(yamlString(method)).append('\n');
                sb.append("        - toD:\n");
                sb.append("            id: ").append(ids.next("toD")).append('\n');
                sb.append("            uri: ").append(yamlString(api.getUrl())).append('\n');
            }
        }

        if (cfg.getExtract() != null) {
            for (Map.Entry<String, String> e : cfg.getExtract().entrySet()) {
                sb.append("        - setProperty:\n");
                sb.append("            id: ").append(ids.next("setProperty")).append('\n');
                sb.append("            name: extracted.").append(e.getKey()).append('\n');
                sb.append("            jsonpath:\n");
                sb.append("              expression: ").append(yamlString(e.getValue())).append('\n');
            }
        }

        DbQueryConfig db = cfg.getDbQuery();
        if (db != null && db.getEntity() != null && !db.getEntity().isBlank()) {
            sb.append("        - log:\n");
            sb.append("            id: ").append(ids.next("log")).append('\n');
            sb.append("            message: ")
              .append(yamlString("DB query " + buildDbDescriptor(db))).append('\n');
            sb.append("        - to:\n");
            sb.append("            id: ").append(ids.next("to")).append('\n');
            sb.append("            uri: bean:dynamicDbQueryProcessor\n");
        }

        if (cfg.getResponse() != null) {
            sb.append("        - to:\n");
            sb.append("            id: ").append(ids.next("to")).append('\n');
            sb.append("            uri: bean:responseTemplateResolver\n");
        }

        return sb.toString();
    }

    private void emitSetHeaderXml(StringBuilder sb, IdGen ids,
                                  String name, String value, boolean preferSimple) {
        sb.append("        <setHeader id=\"").append(ids.next("setHeader"))
          .append("\" name=\"").append(xmlEscape(name)).append("\">\n");
        String tag = preferSimple ? exprTag(value) : "constant";
        sb.append("            <").append(tag).append('>')
          .append(xmlEscape(value == null ? "" : value))
          .append("</").append(tag).append(">\n");
        sb.append("        </setHeader>\n");
    }

    private void emitSetHeaderYaml(StringBuilder sb, IdGen ids,
                                   String name, String value, boolean preferSimple) {
        sb.append("        - setHeader:\n");
        sb.append("            id: ").append(ids.next("setHeader")).append('\n');
        sb.append("            name: ").append(yamlString(name)).append('\n');
        String tag = preferSimple ? exprTag(value) : "constant";
        sb.append("            ").append(tag).append(": ")
          .append(yamlString(value == null ? "" : value)).append('\n');
    }

    private String exprTag(String value) {
        return value != null && HAS_PLACEHOLDER.matcher(value).find() ? "simple" : "constant";
    }

    private String buildDbDescriptor(DbQueryConfig db) {
        StringBuilder s = new StringBuilder(db.getEntity());
        if (db.getFilters() != null && !db.getFilters().isEmpty()) {
            s.append(" WHERE ");
            for (int i = 0; i < db.getFilters().size(); i++) {
                QueryFilter f = db.getFilters().get(i);
                if (i > 0) s.append(" AND ");
                s.append(f.getField()).append(' ').append(f.getOp()).append(' ')
                 .append(f.getValue() == null ? "?" : f.getValue());
            }
        }
        if (db.getOrderBy() != null && !db.getOrderBy().isBlank()) {
            s.append(" ORDER BY ").append(db.getOrderBy());
            if (db.getOrderDir() != null) s.append(' ').append(db.getOrderDir());
        }
        if (db.getLimit() != null) s.append(" LIMIT ").append(db.getLimit());
        return s.toString();
    }

    private String valueToString(Object o) {
        return o == null ? "" : o.toString();
    }

    private String xmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String yamlString(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static final class IdGen {
        private final AtomicInteger seq = new AtomicInteger(1);
        String next(String prefix) {
            return prefix + "-" + seq.getAndIncrement();
        }
    }
}
