package com.vn.jmixcamel.route;

import com.vn.jmixcamel.service.FlowDispatcher;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Single Camel route. Dispatches to the FlowDispatcher which executes nodes in
 * topological order computed from the canvas edges. Replaces the old hard-coded
 * REST → EXTRACT → DB → RESPONSE pipeline.
 */
@Component
public class DynamicExecutionRoute extends RouteBuilder {

    public static final String URI = "direct:dynamic-execute";

    private final FlowDispatcher flowDispatcher;

    public DynamicExecutionRoute(FlowDispatcher flowDispatcher) {
        this.flowDispatcher = flowDispatcher;
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
                    String stage = exchange.getProperty("stage", String.class);
                    exchange.getIn().setBody(Map.of(
                            "error", stage == null ? "EXECUTION_FAILED" : stage,
                            "message", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
                    ));
                });

        from(URI)
                .routeId("dynamic-execute")
                .log("Incoming flow: ${exchangeProperty.execConfig}")
                .process(flowDispatcher);
    }
}
