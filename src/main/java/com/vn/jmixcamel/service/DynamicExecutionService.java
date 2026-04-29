package com.vn.jmixcamel.service;

import com.vn.jmixcamel.dto.ExecutionConfig;
import com.vn.jmixcamel.route.DynamicExecutionRoute;
import com.vn.jmixcamel.security.ConfigSecurityValidator;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DynamicExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DynamicExecutionService.class);

    private final ProducerTemplate producerTemplate;
    private final ConfigSecurityValidator configSecurityValidator;

    public DynamicExecutionService(
            ProducerTemplate producerTemplate,
            ConfigSecurityValidator configSecurityValidator
    ) {
        this.producerTemplate = producerTemplate;
        this.configSecurityValidator = configSecurityValidator;
    }

    public Object execute(ExecutionConfig config) {
        configSecurityValidator.validate(config);
        log.info("Executing dynamic config: api.url={}, dbQuery.entity={}",
                config.getApi().getUrl(),
                config.getDbQuery() == null ? null : config.getDbQuery().getEntity());

        return producerTemplate.send(DynamicExecutionRoute.URI, exchange -> {
            exchange.setPattern(ExchangePattern.InOut);
            exchange.setProperty("execConfig", config);
            exchange.getIn().setBody(Map.of());
        }).getMessage().getBody();
    }
}
