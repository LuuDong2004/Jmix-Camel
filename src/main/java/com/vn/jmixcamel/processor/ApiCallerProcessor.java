package com.vn.jmixcamel.processor;

import com.vn.jmixcamel.dto.ApiConfig;
import com.vn.jmixcamel.dto.ExecutionConfig;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ApiCallerProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(ApiCallerProcessor.class);
    private static final Pattern VAR = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public void process(Exchange exchange) {
        ExecutionConfig config = exchange.getProperty("execConfig", ExecutionConfig.class);
        ApiConfig api = config.getApi();
        Map<String, Object> input = config.getInput() == null ? Map.of() : config.getInput();

        String resolvedUrl = resolveVars(api.getUrl(), input);

        URI uri = URI.create(resolvedUrl);
        String camelHttpUri = stripQuery(uri);
        String queryString = uri.getRawQuery();

        Map<String, String> headers = api.getHeaders() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(api.getHeaders());

        headers.forEach((k, v) -> exchange.getIn().setHeader(k, resolveVars(v, input)));

        exchange.getIn().setHeader(Exchange.HTTP_METHOD, api.getMethod().toUpperCase());
        exchange.getIn().setHeader(Exchange.HTTP_URI, camelHttpUri);
        if (queryString != null && !queryString.isBlank()) {
            exchange.getIn().setHeader(Exchange.HTTP_QUERY, queryString);
        } else {
            exchange.getIn().removeHeader(Exchange.HTTP_QUERY);
        }

        Object body = api.getBody();
        if (body != null && !"GET".equalsIgnoreCase(api.getMethod())) {
            exchange.getIn().setBody(resolveBody(body, input));
            if (!headers.containsKey("Content-Type")) {
                exchange.getIn().setHeader("Content-Type", "application/json");
            }
        } else {
            exchange.getIn().setBody(null);
        }

        log.info("API call prepared: method={}, url={}", api.getMethod(), resolvedUrl);
    }

    private Object resolveBody(Object body, Map<String, Object> input) {
        if (body instanceof String s) {
            return resolveVars(s, input);
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

    private String resolveVars(String template, Map<String, Object> vars) {
        if (template == null) return null;
        Matcher m = VAR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1).trim();
            Object v = vars.get(key);
            if (v == null) {
                throw new IllegalArgumentException("Missing input variable: " + key);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    URLEncoder.encode(v.toString(), StandardCharsets.UTF_8)
                            .replace("+", "%20")
            ));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
