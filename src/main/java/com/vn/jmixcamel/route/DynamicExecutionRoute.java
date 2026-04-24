package com.vn.jmixcamel.route;

import com.vn.jmixcamel.dto.ExecutionResult;
import com.vn.jmixcamel.processor.ApiCallerProcessor;
import com.vn.jmixcamel.processor.DynamicDbLookupProcessor;
import com.vn.jmixcamel.processor.DynamicExtractProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DynamicExecutionRoute extends RouteBuilder {

    public static final String URI = "direct:dynamic-execute";

    private final ApiCallerProcessor apiCallerProcessor;
    private final DynamicExtractProcessor dynamicExtractProcessor;
    private final DynamicDbLookupProcessor dynamicDbLookupProcessor;

    public DynamicExecutionRoute(
            ApiCallerProcessor apiCallerProcessor,
            DynamicExtractProcessor dynamicExtractProcessor,
            DynamicDbLookupProcessor dynamicDbLookupProcessor
    ) {
        this.apiCallerProcessor = apiCallerProcessor;
        this.dynamicExtractProcessor = dynamicExtractProcessor;
        this.dynamicDbLookupProcessor = dynamicDbLookupProcessor;
    }

    @Override
    public void configure() {
        onException(IllegalArgumentException.class)
                .handled(true)
                .log("Validation error: ${exception.message}")
                .process(exchange -> {
                    Exception ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    exchange.getIn().setBody(Map.of(
                            "error", "VALIDATION_FAILED",
                            "message", ex.getMessage()
                    ));
                });

        onException(Exception.class)
                .handled(true)
                .log("Execution failed: ${exception.message}")
                .process(exchange -> {
                    Exception ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    String stage = classifyStage(exchange);
                    exchange.getIn().setBody(Map.of(
                            "error", stage,
                            "message", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
                    ));
                });

        from(URI)
                .routeId("dynamic-execute")
                .log("Incoming config: ${exchangeProperty.execConfig}")
                .setProperty("stage", constant("API_FAILED"))
                .process(apiCallerProcessor)
                .to("http://dummy?throwExceptionOnFailure=true")
                .convertBodyTo(String.class)
                .log("API response received (${body.length()} chars)")
                .setProperty("stage", constant("EXTRACT_FAILED"))
                .process(dynamicExtractProcessor)
                .setProperty("stage", constant("DB_LOOKUP_FAILED"))
                .process(dynamicDbLookupProcessor)
                .setProperty("stage", constant("OK"))
                .process(exchange -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> extracted = exchange.getProperty("extracted", Map.class);
                    Object dbResult = exchange.getProperty("dbResult");
                    exchange.getIn().setBody(new ExecutionResult(extracted, dbResult));
                });
    }

    private String classifyStage(Exchange exchange) {
        String stage = exchange.getProperty("stage", String.class);
        return stage == null ? "EXECUTION_FAILED" : stage;
    }
}
