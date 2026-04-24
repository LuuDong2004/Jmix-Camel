package com.vn.jmixcamel.route;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class HelloRoute extends RouteBuilder {
    @Override
    public void configure() {
        from("direct:hello")
                .log("Input: ${body}")
                .setBody(simple("Xin chào ${body}"))
                .log("Output: ${body}");
    }
}
