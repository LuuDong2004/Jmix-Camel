package com.vn.jmixcamel.service.query;

import java.util.Map;
import java.util.Set;

public interface QueryableEntity {

    String type();

    String jpqlName();

    Class<?> entityClass();

    Set<String> allowedFields();

    Map<String, Object> toMap(Object entity);
}
