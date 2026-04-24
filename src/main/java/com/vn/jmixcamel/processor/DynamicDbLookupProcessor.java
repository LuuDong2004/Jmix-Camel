package com.vn.jmixcamel.processor;

import com.vn.jmixcamel.dto.DbLookupConfig;
import com.vn.jmixcamel.dto.ExecutionConfig;
import com.vn.jmixcamel.service.lookup.LookupHandler;
import com.vn.jmixcamel.service.lookup.LookupRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DynamicDbLookupProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(DynamicDbLookupProcessor.class);

    private final LookupRegistry lookupRegistry;

    public DynamicDbLookupProcessor(LookupRegistry lookupRegistry) {
        this.lookupRegistry = lookupRegistry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) {
        ExecutionConfig config = exchange.getProperty("execConfig", ExecutionConfig.class);
        DbLookupConfig dbLookup = config.getDbLookup();
        Map<String, Object> extracted = exchange.getProperty("extracted", Map.class);

        if (dbLookup == null) {
            exchange.setProperty("dbResult", null);
            return;
        }

        LookupHandler handler = lookupRegistry.require(dbLookup.getType());
        Object result = handler.lookup(dbLookup.getBy(), extracted);

        log.info("DB lookup type={} by={} result={}", dbLookup.getType(), dbLookup.getBy(), result);
        exchange.setProperty("dbResult", result);
    }
}
