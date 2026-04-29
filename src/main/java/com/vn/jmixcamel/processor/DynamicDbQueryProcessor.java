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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DynamicDbQueryProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(DynamicDbQueryProcessor.class);

    private final QueryExecutor queryExecutor;
    private final ResponseTemplateResolver templateResolver;

    public DynamicDbQueryProcessor(QueryExecutor queryExecutor,
                                   ResponseTemplateResolver templateResolver) {
        this.queryExecutor = queryExecutor;
        this.templateResolver = templateResolver;
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
        scope.put("input", config.getInput());
        scope.put("extracted", exchange.getProperty("extracted", Map.class));

        DbQueryConfig resolvedCfg = resolveFilterValues(cfg, scope);

        List<Map<String, Object>> results = queryExecutor.execute(resolvedCfg);
        log.info("DB query entity={} returned {} rows", cfg.getEntity(), results.size());

        exchange.setProperty("dbResult", results);
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
