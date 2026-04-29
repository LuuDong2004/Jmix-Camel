package com.vn.jmixcamel.service.query;

import com.vn.jmixcamel.dto.DbQueryConfig;
import com.vn.jmixcamel.dto.QueryFilter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a simple SELECT statement into a DbQueryConfig used by the UI form.
 * Supported shape:
 *   SELECT [cols] FROM &lt;entity&gt; [alias] [WHERE c1 op v1 AND c2 op v2 ...] [ORDER BY col [ASC|DESC]] [LIMIT n]
 * Operators: =, !=, &lt;&gt;, &gt;, &lt;, &gt;=, &lt;=, LIKE
 * Values: 'quoted', ${placeholder}, number, identifier
 *
 * This is a UI helper only — runtime safety still goes through QueryExecutor's
 * whitelist + parameter binding.
 */
@Component
public class SqlQueryParser {

    private static final Pattern SELECT_FROM = Pattern.compile(
            "^\\s*SELECT\\s+(?<cols>.+?)\\s+FROM\\s+(?<entity>[A-Za-z_][A-Za-z0-9_]*)" +
                    "(?:\\s+(?:AS\\s+)?(?<alias>[A-Za-z_][A-Za-z0-9_]*))?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern WHERE_CLAUSE = Pattern.compile(
            "\\bWHERE\\b(?<where>.+?)(?=\\bORDER\\s+BY\\b|\\bLIMIT\\b|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern ORDER_BY_CLAUSE = Pattern.compile(
            "\\bORDER\\s+BY\\s+(?<col>[A-Za-z_][A-Za-z0-9_.]*)\\s*(?<dir>ASC|DESC)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LIMIT_CLAUSE = Pattern.compile(
            "\\bLIMIT\\s+(?<n>\\d+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FILTER = Pattern.compile(
            "^\\s*(?<field>[A-Za-z_][A-Za-z0-9_.]*)\\s*" +
                    "(?<op>!=|<>|>=|<=|=|>|<|LIKE)\\s*" +
                    "(?<val>'(?:[^']|'')*'|\\$\\{[^}]+}|-?\\d+(?:\\.\\d+)?|[A-Za-z_][A-Za-z0-9_]*)\\s*$",
            Pattern.CASE_INSENSITIVE);

    public DbQueryConfig parse(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL trống");
        }
        String trimmed = sql.trim().replaceAll(";\\s*$", "");

        Matcher head = SELECT_FROM.matcher(trimmed);
        if (!head.find()) {
            throw new IllegalArgumentException(
                    "Câu lệnh phải bắt đầu bằng: SELECT ... FROM <entity>");
        }

        DbQueryConfig cfg = new DbQueryConfig();
        cfg.setEntity(head.group("entity").toLowerCase());

        String tail = trimmed.substring(head.end());

        Matcher whereM = WHERE_CLAUSE.matcher(tail);
        if (whereM.find()) {
            cfg.setFilters(parseFilters(whereM.group("where")));
        }

        Matcher orderM = ORDER_BY_CLAUSE.matcher(tail);
        if (orderM.find()) {
            cfg.setOrderBy(stripAlias(orderM.group("col")));
            String dir = orderM.group("dir");
            cfg.setOrderDir(dir == null ? "ASC" : dir.toUpperCase());
        }

        Matcher limitM = LIMIT_CLAUSE.matcher(tail);
        if (limitM.find()) {
            cfg.setLimit(Integer.parseInt(limitM.group("n")));
        }

        return cfg;
    }

    private List<QueryFilter> parseFilters(String whereExpr) {
        List<String> chunks = splitOnAnd(whereExpr);
        List<QueryFilter> out = new ArrayList<>();
        for (String chunk : chunks) {
            String c = chunk.trim();
            if (c.isEmpty()) continue;
            Matcher m = FILTER.matcher(c);
            if (!m.matches()) {
                throw new IllegalArgumentException(
                        "Không parse được điều kiện: '" + c + "'. Hỗ trợ: field op value");
            }
            QueryFilter f = new QueryFilter();
            f.setField(stripAlias(m.group("field")));
            f.setOp(normalizeOp(m.group("op")));
            f.setValue(unquote(m.group("val")));
            out.add(f);
        }
        return out;
    }

    /** Splits on the AND keyword, ignoring matches inside single-quoted strings. */
    private List<String> splitOnAnd(String expr) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (c == '\'') {
                inQuote = !inQuote;
                cur.append(c);
                i++;
                continue;
            }
            if (!inQuote
                    && i + 3 < expr.length()
                    && (i == 0 || Character.isWhitespace(expr.charAt(i - 1)))
                    && expr.regionMatches(true, i, "AND", 0, 3)
                    && Character.isWhitespace(expr.charAt(i + 3))) {
                parts.add(cur.toString());
                cur.setLength(0);
                i += 3;
                continue;
            }
            cur.append(c);
            i++;
        }
        if (cur.length() > 0) parts.add(cur.toString());
        return parts;
    }

    private String stripAlias(String ref) {
        int dot = ref.lastIndexOf('.');
        return dot >= 0 ? ref.substring(dot + 1) : ref;
    }

    private String normalizeOp(String op) {
        String t = op.trim();
        if ("<>".equals(t)) return "!=";
        if (t.equalsIgnoreCase("LIKE")) return "like";
        return t;
    }

    private String unquote(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.length() >= 2 && t.charAt(0) == '\'' && t.charAt(t.length() - 1) == '\'') {
            return t.substring(1, t.length() - 1).replace("''", "'");
        }
        return t;
    }
}
