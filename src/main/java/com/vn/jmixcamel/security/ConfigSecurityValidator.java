package com.vn.jmixcamel.security;

import com.vn.jmixcamel.dto.ApiConfig;
import com.vn.jmixcamel.dto.DbQueryConfig;
import com.vn.jmixcamel.dto.ExecutionConfig;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ConfigSecurityValidator {

    private static final Set<String> ALLOWED_URL_SCHEMES = Set.of("http", "https");

    private static final Set<String> BLOCKED_METHODS = Set.of(
            "TRACE",
            "CONNECT"
    );

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^}]+}");

    public void validate(ExecutionConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config is required");
        }
        validateApi(config.getApi());
        validateDbQuery(config.getDbQuery());
    }

    private void validateApi(ApiConfig api) {
        if (api == null) {
            throw new IllegalArgumentException("config.api is required");
        }

        String method = api.getMethod();
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("config.api.method is required");
        }
        if (BLOCKED_METHODS.contains(method.toUpperCase())) {
            throw new IllegalArgumentException("HTTP method not allowed: " + method);
        }

        String url = api.getUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("config.api.url is required");
        }

        String urlForParsing = PLACEHOLDER.matcher(url).replaceAll("x");
        URI uri;
        try {
            uri = new URI(urlForParsing);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("config.api.url is not a valid URI: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_URL_SCHEMES.contains(scheme.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Only http/https schemes are allowed. Got: " + scheme
            );
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("config.api.url must have a host");
        }
    }

    private void validateDbQuery(DbQueryConfig dbQuery) {
        if (dbQuery == null) return;
        if (dbQuery.getEntity() == null || dbQuery.getEntity().isBlank()) {
            throw new IllegalArgumentException("config.dbQuery.entity is required");
        }
        // Entity whitelist + field whitelist enforced by QueryExecutor / QueryableEntityRegistry
    }
}
