package com.vn.jmixcamel.service;

import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CamelService {
    private final ProducerTemplate producerTemplate;

    public CamelService(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    public String sayHello(String name) {
        return producerTemplate.requestBody("direct:hello", name, String.class);
    }
    public String getUserById(String userId) {
        return producerTemplate.requestBody("direct:getUser", userId, String.class);
    }
    public String getUser(Map<String, Object> request) {
        return producerTemplate.requestBody("direct:getUser", request, String.class);
    }

    public String executeApi(Map<String, Object> request) {
        return producerTemplate.requestBody("direct:executeApi", request, String.class);
    }
}
