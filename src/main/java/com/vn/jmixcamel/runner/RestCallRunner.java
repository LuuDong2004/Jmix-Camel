package com.vn.jmixcamel.runner;

import com.vn.jmixcamel.service.ResponseTemplateResolver;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RestCallRunner {

    private static final Logger log = LoggerFactory.getLogger(RestCallRunner.class);
    private static final Pattern VAR = Pattern.compile("\\$\\{([^}]+)}");

    private final ProducerTemplate producerTemplate;
    private final ResponseTemplateResolver templateResolver;

    public RestCallRunner(ProducerTemplate producerTemplate, ResponseTemplateResolver templateResolver) {
        this.producerTemplate = producerTemplate;
        this.templateResolver = templateResolver;
    }

    @SuppressWarnings("unchecked")
    public String run(Map<String, Object> data, Map<String, Object> scope, Exchange parent) {
        String method = String.valueOf(data.getOrDefault("method", "GET")).toUpperCase();
        String url = (String) data.get("url");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("REST_CALL.url is required");
        }

        String resolvedUrl = resolveUrl(url, scope);
        URI uri = URI.create(resolvedUrl);
        String camelHttpUri = stripQuery(uri);
        String queryString = uri.getRawQuery();

        // Headers from FE: List<{key,value}>
        Map<String, String> resolvedHeaders = new LinkedHashMap<>();
        Object headersObj = data.get("headers");
        if (headersObj instanceof List<?> list) {
            for (Object h : list) {
                if (h instanceof Map<?, ?> m) {
                    String k = (String) m.get("key");
                    String v = (String) m.get("value");
                    if (k != null && !k.isBlank()) {
                        resolvedHeaders.put(k, resolveString(v, scope));
                    }
                }
            }
        }

        // Body: raw JSON. Substitutes ${X} placeholders if present (power-user feature —
        // FE does not expose a picker, but legacy flows and inline edits still resolve).
        // GET/HEAD never carry a body.
        Object bodyToSend = null;
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            String raw = (String) data.get("body");
            if (raw != null && !raw.isBlank()) {
                bodyToSend = raw.contains("${") ? resolveString(raw, scope) : raw;
            }
            if (bodyToSend != null
                    && !resolvedHeaders.containsKey("Content-Type")
                    && !resolvedHeaders.containsKey("content-type")) {
                resolvedHeaders.put("Content-Type", "application/json");
            }
        }

        log.info("HTTP {} {} (headers={})", method, resolvedUrl, resolvedHeaders.keySet());
        final String fMethod = method;
        final Object fBody = bodyToSend;
        Exchange ex = producerTemplate.send("http://dummy?throwExceptionOnFailure=true", e -> {
            e.setPattern(ExchangePattern.InOut);
            e.getIn().setHeader(Exchange.HTTP_METHOD, fMethod);
            e.getIn().setHeader(Exchange.HTTP_URI, camelHttpUri);
            if (queryString != null && !queryString.isBlank()) {
                e.getIn().setHeader(Exchange.HTTP_QUERY, queryString);
            }
            resolvedHeaders.forEach((k, v) -> e.getIn().setHeader(k, v));
            e.getIn().setBody(fBody);
        });
        if (ex.getException() != null) {
            throw new RuntimeException("HTTP failed: " + ex.getException().getMessage(), ex.getException());
        }
        return ex.getMessage().getBody(String.class);
    }

    private String resolveString(String template, Map<String, Object> scope) {
        if (template == null) return null;
        return resolveVars(template, scope, false);
    }

    private String resolveUrl(String template, Map<String, Object> scope) {
        return resolveVars(template, scope, true);
    }

    private String resolveVars(String template, Map<String, Object> scope, boolean urlEncode) {
        Matcher m = VAR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String path = m.group(1).trim();
            Object v = templateResolver.lookup(path, scope);
            if (v == null) throw new IllegalArgumentException("Missing variable: " + path);
            String s = v.toString();
            if (urlEncode) {
                s = URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(s));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String stripQuery(URI uri) {
        StringBuilder sb = new StringBuilder();
        sb.append(uri.getScheme()).append("://").append(uri.getHost());
        if (uri.getPort() != -1) sb.append(':').append(uri.getPort());
        if (uri.getRawPath() != null) sb.append(uri.getRawPath());
        return sb.toString();
    }
}
