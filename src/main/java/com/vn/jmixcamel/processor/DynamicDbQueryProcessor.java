package com.vn.jmixcamel.processor;

import com.vn.jmixcamel.dto.DbQueryConfig;
import com.vn.jmixcamel.dto.ExecutionConfig;
import com.vn.jmixcamel.dto.QueryFilter;
import com.vn.jmixcamel.service.ResponseTemplateResolver;
import com.vn.jmixcamel.service.query.QueryExecutor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DynamicDbQueryProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(DynamicDbQueryProcessor.class);
    private static final Pattern VAR = Pattern.compile("\\$\\{([^}]+)}");

    private final QueryExecutor queryExecutor;
    private final ResponseTemplateResolver templateResolver;
    private final JdbcTemplate mainJdbcTemplate;
    private final JdbcTemplate testJdbcTemplate;

    public DynamicDbQueryProcessor(QueryExecutor queryExecutor,
                                   ResponseTemplateResolver templateResolver,
                                   JdbcTemplate mainJdbcTemplate,
                                   @Qualifier("testJdbcTemplate") JdbcTemplate testJdbcTemplate) {
        this.queryExecutor = queryExecutor;
        this.templateResolver = templateResolver;
        this.mainJdbcTemplate = mainJdbcTemplate;
        this.testJdbcTemplate = testJdbcTemplate;
    }

    private JdbcTemplate selectTemplate(String name) {
        if (name == null || name.isBlank() || "main".equalsIgnoreCase(name)) return mainJdbcTemplate;
        if ("test".equalsIgnoreCase(name)) return testJdbcTemplate;
        throw new IllegalArgumentException("Unknown datasource: " + name + ". Allowed: main, test");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) {
        ExecutionConfig config = exchange.getProperty("execConfig", ExecutionConfig.class);
        DbQueryConfig cfg = config.getDbQuery();
        if (cfg == null) {
            exchange.setProperty("dbResult", null);
            return;
        }

        Map<String, Object> scope = new HashMap<>();
        scope.put("input",     config.getInput()  == null ? Map.of() : config.getInput());
        scope.put("output",    config.getOutput() == null ? Map.of() : config.getOutput());
        scope.put("object",    config.getObject() == null ? Map.of() : config.getObject());
        scope.put("extracted", exchange.getProperty("extracted", Map.class));

        List<Map<String, Object>> rows;
        if (cfg.getSql() != null && !cfg.getSql().isBlank()) {
            JdbcTemplate jdbc = selectTemplate(cfg.getDatasource());
            rows = executeRawSql(cfg.getSql(), scope, jdbc);
            log.info("Raw SQL on datasource={} returned {} rows", cfg.getDatasource(), rows.size());
        } else {
            DbQueryConfig resolvedCfg = resolveFilterValues(cfg, scope);
            rows = queryExecutor.execute(resolvedCfg);
            log.info("DB query entity={} returned {} rows", cfg.getEntity(), rows.size());
        }

        Object dbResult = applyOutputFields(rows, cfg.getOutputFields());
        exchange.setProperty("dbResult", dbResult);
    }

    private List<Map<String, Object>> executeRawSql(String sql, Map<String, Object> scope, JdbcTemplate jdbc) {
        List<Object> params = new ArrayList<>();
        Matcher m = VAR.matcher(sql);
        StringBuilder parameterized = new StringBuilder();
        while (m.find()) {
            Object v = templateResolver.lookup(m.group(1).trim(), scope);
            if (v == null) {
                throw new IllegalArgumentException("Missing variable in SQL: " + m.group(1));
            }
            params.add(v);
            m.appendReplacement(parameterized, "?");
        }
        m.appendTail(parameterized);

        String finalSql = parameterized.toString();
        log.info("Executing raw SQL: {} with params: {}", finalSql, params);
        return jdbc.queryForList(finalSql, params.toArray());
    }

    /**
     * Project each row through {alias → column} map. If outputFields is empty, returns rows as-is.
     */
    private Object applyOutputFields(List<Map<String, Object>> rows, Map<String, String> outputFields) {
        if (outputFields == null || outputFields.isEmpty()) {
            return rows;
        }
        List<Map<String, Object>> projected = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> out = new LinkedHashMap<>();
            outputFields.forEach((alias, column) -> out.put(alias, row.get(column)));
            projected.add(out);
        }
        return projected;
    }

    private DbQueryConfig resolveFilterValues(DbQueryConfig cfg, Map<String, Object> scope) {
        if (cfg.getFilters() == null || cfg.getFilters().isEmpty()) return cfg;

        DbQueryConfig out = new DbQueryConfig();
        out.setEntity(cfg.getEntity());
        out.setOrderBy(cfg.getOrderBy());
        out.setOrderDir(cfg.getOrderDir());
        out.setLimit(cfg.getLimit());

        List<QueryFilter> resolvedFilters = new ArrayList<>(cfg.getFilters().size());
        for (QueryFilter f : cfg.getFilters()) {
            QueryFilter r = new QueryFilter();
            r.setField(f.getField());
            r.setOp(f.getOp());
            r.setValue(templateResolver.resolve(f.getValue(), scope));
            resolvedFilters.add(r);
        }
        out.setFilters(resolvedFilters);
        return out;
    }
}
