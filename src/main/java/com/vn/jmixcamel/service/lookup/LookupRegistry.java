package com.vn.jmixcamel.service.lookup;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class LookupRegistry {

    private final Map<String, LookupHandler> handlers;

    public LookupRegistry(List<LookupHandler> handlers) {
        this.handlers = handlers.stream().collect(Collectors.toMap(
                h -> h.type().toLowerCase(),
                h -> h
        ));
    }

    public LookupHandler require(String type) {
        if (type == null) {
            throw new IllegalArgumentException("lookup type is required");
        }
        LookupHandler handler = handlers.get(type.toLowerCase());
        if (handler == null) {
            throw new IllegalArgumentException(
                    "No lookup handler registered for type: " + type +
                            ". Registered: " + handlers.keySet()
            );
        }
        return handler;
    }
}
