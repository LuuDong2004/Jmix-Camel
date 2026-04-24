package com.vn.jmixcamel.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DynamicApiRouter extends RouteBuilder {
    @Override
    public void configure() {
        onException(Exception.class)
                .handled(true)
                .process(exchange -> {
                    Exception ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    exchange.getIn().setBody("Error: " + ex.getMessage());
                });

        from("direct:executeApi")
                .routeId("dynamic-api-route")
                .process(exchange -> {
                    Map<?, ?> body = exchange.getIn().getBody(Map.class);

                    String method = (String) body.get("method");
                    String urlTemplate = (String) body.get("url");
                    String userId = (String) body.get("userId");

                    String finalUrl = urlTemplate.replace("${userId}", userId);

                    exchange.setProperty("method", method);
                    exchange.setProperty("finalUrl", finalUrl);
                })
                .setHeader(Exchange.HTTP_METHOD, simple("${exchangeProperty.method}"))
                .setHeader(Exchange.HTTP_URI, simple("${exchangeProperty.finalUrl}"))
                .to("http://dummy")
                .process(exchange -> {
                    String json = exchange.getIn().getBody(String.class);

                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode node = mapper.readTree(json);

                    if (node.has("firstName") && node.has("lastName")) {
                        String name = node.get("firstName").asText();
                        String name2 = node.get("lastName").asText();
                        exchange.getIn().setBody("firstName: " + name + "\nlastName: " + name2);
                    } else {
                        exchange.getIn().setBody(json);
                    }
                });
    }
}
