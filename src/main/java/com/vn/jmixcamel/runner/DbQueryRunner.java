package com.vn.jmixcamel.runner;

import com.vn.jmixcamel.dto.DbQueryConfig;
import com.vn.jmixcamel.dto.QueryFilter;
import com.vn.jmixcamel.service.ResponseTemplateResolver;
import com.vn.jmixcamel.service.query.QueryExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DbQueryRunner {

    private static final Logger log = LoggerFactory.getLogger(DbQueryRunner.class);
    private static final Pattern VAR = Pattern.compile("\\$\\{([^}]+)}");

    private final QueryExecutor queryExecutor;
    private final ResponseTemplateResolver templateResolver;
    private final JdbcTemplate mainJdbcTemplate;
    private final JdbcTemplate testJdbcTemplate;

    public DbQueryRunner(QueryExecutor queryExecutor,
                         ResponseTemplateResolver templateResolver,
                         JdbcTemplate mainJdbcTemplate,
                         @Qualifier("testJdbcTemplate") JdbcTemplate testJdbcTemplate) {
        this.queryExecutor = queryExecutor;
        this.templateResolver = templateResolver;
        this.mainJdbcTemplate = mainJdbcTemplate;
        this.testJdbcTemplate = testJdbcTemplate;
    }

    @SuppressWarnings("unchecked")
    public Object run(Map<String, Object> data, Map<String, Object> scope) {
        String mode = (String) data.getOrDefault("mode", data.get("sql") != null ? "sql" : "builder");
        String sql = (String) data.get("sql");

        List<Map<String, Object>> rows;
        if ("sql".equals(mode) && sql != null && !sql.isBlank()) {
            JdbcTemplate jdbc = pickTemplate((String) data.get("datasource"));
            rows = executeRawSql(sql, scope, jdbc);
            log.info("Raw SQL on datasource={} returned {} rows", data.get("datasource"), rows.size());
        } else {
            DbQueryConfig cfg = buildBuilderConfig(data);
            cfg = resolveFilterValues(cfg, scope);
            rows = queryExecutor.execute(cfg);
            log.info("Builder query entity={} returned {} rows", cfg.getEntity(), rows.size());
        }

        // Output fields projection (alias → column)
        Object outputFieldsObj = data.get("outputFields");
        Map<String, String> outputFields = null;
        if (outputFieldsObj instanceof List<?> list) {
            outputFields = new LinkedHashMap<>();
            for (Object r : list) {
                if (r instanceof Map<?, ?> m) {
                    String alias = (String) m.get("key");
                    String col = (String) m.get("value");
                    if (alias != null && !alias.isBlank()) outputFields.put(alias, col != null && !col.isBlank() ? col : alias);
                }
            }
        } else if (outputFieldsObj instanceof Map<?, ?> m) {
            outputFields = (Map<String, String>) m;
        }

        if (outputFields == null || outputFields.isEmpty()) return rows;
        List<Map<String, Object>> projected = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> out = new LinkedHashMap<>();
            outputFields.forEach((alias, col) -> out.put(alias, row.get(col)));
            projected.add(out);
        }
        return projected;
    }

    private JdbcTemplate pickTemplate(String name) {
        if (name == null || name.isBlank() || "main".equalsIgnoreCase(name)) return mainJdbcTemplate;
        if ("test".equalsIgnoreCase(name)) return testJdbcTemplate;
        throw new IllegalArgumentException("Unknown datasource: " + name + ". Allowed: main, test");
    }

    private List<Map<String, Object>> executeRawSql(String sql, Map<String, Object> scope, JdbcTemplate jdbc) {
        List<Object> params = new ArrayList<>();
        Matcher m = VAR.matcher(sql);
        StringBuilder parameterized = new StringBuilder();
        while (m.find()) {
            Object v = templateResolver.lookup(m.group(1).trim(), scope);
            if (v == null) throw new IllegalArgumentException("Missing variable in SQL: " + m.group(1));
            params.add(v);
            m.appendReplacement(parameterized, "?");
        }
        m.appendTail(parameterized);
        log.info("Executing SQL: {} with params: {}", parameterized, params);
        return jdbc.queryForList(parameterized.toString(), params.toArray());
    }

    private DbQueryConfig buildBuilderConfig(Map<String, Object> data) {
        DbQueryConfig cfg = new DbQueryConfig();
        cfg.setEntity((String) data.get("entity"));
        cfg.setOrderBy(emptyToNull((String) data.get("orderBy")));
        cfg.setOrderDir(emptyToNull((String) data.get("orderDir")));
        Object lim = data.get("limit");
        if (lim instanceof Integer i) cfg.setLimit(i);
        else if (lim instanceof Number n) cfg.setLimit(n.intValue());
        else if (lim instanceof String s && !s.isBlank()) cfg.setLimit(Integer.parseInt(s));

        Object filtersObj = data.get("filters");
        if (filtersObj instanceof List<?> list) {
            List<QueryFilter> filters = new ArrayList<>();
            for (Object f : list) {
                if (f instanceof Map<?, ?> fm) {
                    String field = (String) fm.get("field");
                    if (field == null || field.isBlank()) continue;
                    QueryFilter qf = new QueryFilter();
                    qf.setField(field);
                    qf.setOp((String) fm.get("op"));
                    qf.setValue(fm.get("value"));
                    filters.add(qf);
                }
            }
            cfg.setFilters(filters);
        }
        return cfg;
    }

    private DbQueryConfig resolveFilterValues(DbQueryConfig cfg, Map<String, Object> scope) {
        if (cfg.getFilters() == null || cfg.getFilters().isEmpty()) return cfg;
        DbQueryConfig out = new DbQueryConfig();
        out.setEntity(cfg.getEntity());
        out.setOrderBy(cfg.getOrderBy());
        out.setOrderDir(cfg.getOrderDir());
        out.setLimit(cfg.getLimit());
        List<QueryFilter> resolved = new ArrayList<>(cfg.getFilters().size());
        for (QueryFilter f : cfg.getFilters()) {
            QueryFilter r = new QueryFilter();
            r.setField(f.getField());
            r.setOp(f.getOp());
            r.setValue(templateResolver.resolve(f.getValue(), scope));
            resolved.add(r);
        }
        out.setFilters(resolved);
        return out;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
