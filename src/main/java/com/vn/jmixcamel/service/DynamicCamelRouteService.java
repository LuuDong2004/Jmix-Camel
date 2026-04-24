package com.vn.jmixcamel.service;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.ResourceHelper;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.camel.spi.RoutesLoader;
@Service
public class DynamicCamelRouteService {
    private final CamelContext camelContext;
    private final ProducerTemplate producerTemplate;
    private final RouteSecurityValidator routeSecurityValidator;

    public DynamicCamelRouteService(
            CamelContext camelContext,
            ProducerTemplate producerTemplate,
            RouteSecurityValidator routeSecurityValidator
    ) {
        this.camelContext = camelContext;
        this.producerTemplate = producerTemplate;
        this.routeSecurityValidator = routeSecurityValidator;
    }

    public String deployRoute(String routeId, String dslType, String content) {
        validateBasic(routeId, dslType, content);
        routeSecurityValidator.validate(content);

        try {
            if (camelContext.getRoute(routeId) != null) {
                camelContext.getRouteController().stopRoute(routeId);
                camelContext.removeRoute(routeId);
            }

            String suffix = "YAML".equalsIgnoreCase(dslType) ? ".yaml" : ".xml";

            Path tempFile = Files.createTempFile(
                    "camel-route-" + routeId + "-",
                    suffix
            );

            Files.writeString(tempFile, content, StandardCharsets.UTF_8);

            RoutesLoader routesLoader = camelContext
                    .getCamelContextExtension()
                    .getContextPlugin(RoutesLoader.class);

            routesLoader.loadRoutes(
                    ResourceHelper.resolveResource(camelContext, tempFile.toUri().toString())
            );

            return "Route deployed: " + routeId;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Cannot deploy route " + routeId + ": " + e.getMessage(),
                    e
            );
        }
    }

    public String executeRoute(String routeId, Object body) {
        try {
            String endpoint = "direct:" + routeId;
            return producerTemplate.requestBody(endpoint, body, String.class);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Cannot execute route " + routeId + ": " + e.getMessage(),
                    e
            );
        }
    }

    private void validateBasic(String routeId, String dslType, String content) {
        if (routeId == null || routeId.isBlank()) {
            throw new IllegalArgumentException("routeId is required");
        }

        if (dslType == null || dslType.isBlank()) {
            throw new IllegalArgumentException("dslType is required");
        }

        if (!dslType.equalsIgnoreCase("XML") && !dslType.equalsIgnoreCase("YAML")) {
            throw new IllegalArgumentException("dslType must be XML or YAML");
        }

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
    }

}
