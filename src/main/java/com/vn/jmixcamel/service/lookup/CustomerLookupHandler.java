package com.vn.jmixcamel.service.lookup;

import com.vn.jmixcamel.entity.Customer;
import com.vn.jmixcamel.service.CustomerService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CustomerLookupHandler implements LookupHandler {

    private final CustomerService customerService;

    public CustomerLookupHandler(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Override
    public String type() {
        return "customer";
    }

    @Override
    public Object lookup(List<String> by, Map<String, Object> extracted) {
        if (by == null || by.isEmpty()) {
            throw new IllegalArgumentException("dbLookup.by is required for customer lookup");
        }

        String name = stringValue(extracted, "name");
        String phone = stringValue(extracted, "phone");

        Optional<Customer> match;
        if (by.contains("name") && by.contains("phone")) {
            requireField("name", name);
            requireField("phone", phone);
            match = customerService.findByNameAndPhone(name, phone);
        } else if (by.contains("phone")) {
            requireField("phone", phone);
            match = customerService.findByPhone(phone);
        } else if (by.contains("name")) {
            requireField("name", name);
            match = customerService.findByName(name);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported 'by' fields for customer lookup: " + by +
                            ". Supported: name, phone"
            );
        }

        return match.map(this::toMap).orElse(null);
    }

    private String stringValue(Map<String, Object> map, String key) {
        Object v = map == null ? null : map.get(key);
        return v == null ? null : v.toString();
    }

    private void requireField(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Cannot lookup customer: missing extracted field '" + field + "'"
            );
        }
    }

    private Map<String, Object> toMap(Customer c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("phone", c.getPhone());
        m.put("email", c.getEmail());
        m.put("segment", c.getSegment());
        return m;
    }
}
