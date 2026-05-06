package com.vn.jmixcamel.processor;

import com.vn.jmixcamel.dto.ApiConfig;
import com.vn.jmixcamel.dto.ExecutionConfig;
import com.vn.jmixcamel.service.ResponseTemplateResolver;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ApiCallerProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(ApiCallerProcessor.class);
    private static final Pattern VAR = Pattern.compile("\\$\\{([^}]+)}");

    private final ResponseTemplateResolver templateResolver;

    public ApiCallerProcessor(ResponseTemplateResolver templateResolver) {
        this.templateResolver = templateResolver;
    }

    @Override
    public void process(Exchange exchange) {
        ExecutionConfig config = exchange.getProperty("execConfig", ExecutionConfig.class);
        ApiConfig api = config.getApi();

        Map<String, Object> scope = buildScope(config);

        String resolvedUrl = resolveVars(api.getUrl(), scope, true);

        URI uri = URI.create(resolvedUrl);
        String camelHttpUri = stripQuery(uri);
        String queryString = uri.getRawQuery();

        Map<String, String> headers = api.getHeaders() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(api.getHeaders());

        headers.forEach((k, v) -> exchange.getIn().setHeader(k, resolveVars(v, scope, false)));

        exchange.getIn().setHeader(Exchange.HTTP_METHOD, api.getMethod().toUpperCase());
        exchange.getIn().setHeader(Exchange.HTTP_URI, camelHttpUri);
        if (queryString != null && !queryString.isBlank()) {
            exchange.getIn().setHeader(Exchange.HTTP_QUERY, queryString);
        } else {
            exchange.getIn().removeHeader(Exchange.HTTP_QUERY);
        }

        Object body = api.getBody();
        if (body != null && !"GET".equalsIgnoreCase(api.getMethod())) {
            exchange.getIn().setBody(resolveBody(body, scope));
            if (!headers.containsKey("Content-Type")) {
                exchange.getIn().setHeader("Content-Type", "application/json");
            }
        } else {
            exchange.getIn().setBody(null);
        }

        log.info("API call prepared: method={}, url={}", api.getMethod(), resolvedUrl);
    }

    private Map<String, Object> buildScope(ExecutionConfig config) {
        Map<String, Object> scope = new HashMap<>();
        scope.put("input",  config.getInput()  == null ? Map.of() : config.getInput());
        scope.put("output", config.getOutput() == null ? Map.of() : config.getOutput());
        scope.put("object", config.getObject() == null ? Map.of() : config.getObject());
        return scope;
    }

    private Object resolveBody(Object body, Map<String, Object> scope) {
        if (body instanceof String s) {
            return resolveVars(s, scope, false);
        }
        return body;
    }

    private String stripQuery(URI uri) {
        StringBuilder sb = new StringBuilder();
        sb.append(uri.getScheme()).append("://").append(uri.getHost());
        if (uri.getPort() != -1) {
            sb.append(':').append(uri.getPort());
        }
        if (uri.getRawPath() != null) {
            sb.append(uri.getRawPath());
        }
        return sb.toString();
    }

    private String resolveVars(String template, Map<String, Object> scope, boolean urlEncode) {
        if (template == null) return null;
        Matcher m = VAR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String path = m.group(1).trim();
            Object v = templateResolver.lookup(path, scope);
            if (v == null) {
                throw new IllegalArgumentException("Missing variable: " + path);
            }
            String s = v.toString();
            if (urlEncode) {
                s = URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(s));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
