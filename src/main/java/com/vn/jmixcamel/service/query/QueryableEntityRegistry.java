package com.vn.jmixcamel.service.query;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Component
public class QueryableEntityRegistry {

    private final Map<String, QueryableEntity> byType;

    public QueryableEntityRegistry(List<QueryableEntity> list) {
        this.byType = list.stream().collect(Collectors.toMap(
                e -> e.type().toLowerCase(),
                e -> e
        ));
    }

    public Set<String> allEntities() {
        return new TreeSet<>(byType.keySet());
    }

    public QueryableEntity require(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("dbQuery.entity is required");
        }
        QueryableEntity e = byType.get(type.toLowerCase());
        if (e == null) {
            throw new IllegalArgumentException(
                    "Entity not allowed: " + type + ". Allowed: " + byType.keySet());
        }
        return e;
    }
}
