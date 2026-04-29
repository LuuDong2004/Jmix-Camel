package com.vn.jmixcamel.service.query;

import com.vn.jmixcamel.dto.DbQueryConfig;
import com.vn.jmixcamel.dto.QueryFilter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Safe JPQL query builder.
 * - Entity name whitelisted via QueryableEntityRegistry
 * - Field names whitelisted per entity
 * - Operators whitelisted
 * - All values bound as named parameters (no SQL injection)
 */
@Component
public class QueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(QueryExecutor.class);

    private static final Set<String> OPS_WITHOUT_PARAM = Set.of("IS_NULL", "IS_NOT_NULL");
    private static final Set<String> OPS_WITH_LIST_PARAM = Set.of("IN", "NOT_IN");
    private static final Set<String> ALL_OPS = Set.of(
            "=", "!=", ">", "<", ">=", "<=", "LIKE", "NOT_LIKE",
            "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"
    );

    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_LIMIT = 50;

    @PersistenceContext
    private EntityManager em;

    private final QueryableEntityRegistry registry;

    public QueryExecutor(QueryableEntityRegistry registry) {
        this.registry = registry;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> execute(DbQueryConfig cfg) {
        if (cfg == null) {
            throw new IllegalArgumentException("dbQuery config is required");
        }
        QueryableEntity qe = registry.require(cfg.getEntity());

        StringBuilder jpql = new StringBuilder("SELECT c FROM ")
                .append(qe.jpqlName()).append(" c");
        Map<String, Object> params = new LinkedHashMap<>();

        List<QueryFilter> filters = cfg.getFilters() == null ? List.of() : cfg.getFilters();
        if (!filters.isEmpty()) {
            jpql.append(" WHERE ");
            for (int i = 0; i < filters.size(); i++) {
                if (i > 0) jpql.append(" AND ");
                appendFilter(jpql, filters.get(i), i, params, qe.allowedFields());
            }
        }

        if (cfg.getOrderBy() != null && !cfg.getOrderBy().isBlank()) {
            requireAllowed(cfg.getOrderBy(), qe.allowedFields(), "orderBy");
            jpql.append(" ORDER BY c.").append(cfg.getOrderBy());
            if ("DESC".equalsIgnoreCase(cfg.getOrderDir())) {
                jpql.append(" DESC");
            } else {
                jpql.append(" ASC");
            }
        }

        int limit = cfg.getLimit() == null ? DEFAULT_LIMIT : cfg.getLimit();
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and " + MAX_LIMIT + ". Got: " + limit);
        }

        String query = jpql.toString();
        log.info("Executing JPQL: {} with params: {}", query, params);

        TypedQuery<?> q = em.createQuery(query, qe.entityClass());
        params.forEach(q::setParameter);
        q.setMaxResults(limit);

        List<?> results = q.getResultList();
        return results.stream().map(qe::toMap).toList();
    }

    private void appendFilter(StringBuilder jpql, QueryFilter f, int index,
                              Map<String, Object> params, Set<String> allowed) {
        if (f.getField() == null || f.getField().isBlank()) {
            throw new IllegalArgumentException("filter.field is required");
        }
        if (f.getOp() == null || f.getOp().isBlank()) {
            throw new IllegalArgumentException("filter.op is required");
        }
        requireAllowed(f.getField(), allowed, "filter.field");

        String op = normalizeOp(f.getOp());
        if (!ALL_OPS.contains(op)) {
            throw new IllegalArgumentException(
                    "Operator not allowed: " + f.getOp() + ". Allowed: " + ALL_OPS);
        }

        String paramName = "p" + index;
        String fieldRef = "c." + f.getField();

        switch (op) {
            case "=":        jpql.append(fieldRef).append(" = :").append(paramName); break;
            case "!=":       jpql.append(fieldRef).append(" <> :").append(paramName); break;
            case ">":        jpql.append(fieldRef).append(" > :").append(paramName); break;
            case "<":        jpql.append(fieldRef).append(" < :").append(paramName); break;
            case ">=":       jpql.append(fieldRef).append(" >= :").append(paramName); break;
            case "<=":       jpql.append(fieldRef).append(" <= :").append(paramName); break;
            case "LIKE":     jpql.append(fieldRef).append(" LIKE :").append(paramName); break;
            case "NOT_LIKE": jpql.append(fieldRef).append(" NOT LIKE :").append(paramName); break;
            case "IN":       jpql.append(fieldRef).append(" IN :").append(paramName); break;
            case "NOT_IN":   jpql.append(fieldRef).append(" NOT IN :").append(paramName); break;
            case "IS_NULL":     jpql.append(fieldRef).append(" IS NULL"); break;
            case "IS_NOT_NULL": jpql.append(fieldRef).append(" IS NOT NULL"); break;
        }

        if (OPS_WITHOUT_PARAM.contains(op)) return;

        if (OPS_WITH_LIST_PARAM.contains(op)) {
            if (!(f.getValue() instanceof List<?> list)) {
                throw new IllegalArgumentException(
                        "Operator " + op + " requires a list value for field " + f.getField());
            }
            if (list.isEmpty()) {
                throw new IllegalArgumentException(
                        "Operator " + op + " requires a non-empty list");
            }
            params.put(paramName, list);
        } else {
            if (f.getValue() == null) {
                throw new IllegalArgumentException(
                        "Operator " + op + " requires a value for field " + f.getField());
            }
            params.put(paramName, f.getValue());
        }
    }

    private String normalizeOp(String op) {
        String trimmed = op.trim().toUpperCase();
        return switch (trimmed) {
            case "NOT LIKE" -> "NOT_LIKE";
            case "IS NULL" -> "IS_NULL";
            case "IS NOT NULL" -> "IS_NOT_NULL";
            case "NOT IN" -> "NOT_IN";
            default -> trimmed;
        };
    }

    private void requireAllowed(String field, Set<String> allowed, String context) {
        if (!allowed.contains(field)) {
            throw new IllegalArgumentException(
                    context + " field not allowed: '" + field +
                            "'. Allowed: " + allowed);
        }
    }
}
