package com.vn.jmixcamel.service.query;

import com.vn.jmixcamel.entity.Customer;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class CustomerQueryableEntity implements QueryableEntity {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "id", "name", "phone", "email", "segment"
    );

    @Override public String type() { return "customer"; }
    @Override public String jpqlName() { return "Customer"; }
    @Override public Class<?> entityClass() { return Customer.class; }
    @Override public Set<String> allowedFields() { return ALLOWED_FIELDS; }

    @Override
    public Map<String, Object> toMap(Object entity) {
        Customer c = (Customer) entity;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("phone", c.getPhone());
        m.put("email", c.getEmail());
        m.put("segment", c.getSegment());
        return m;
    }
}
