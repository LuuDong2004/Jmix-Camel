package com.vn.jmixcamel.security;

import com.vn.jmixcamel.dto.ApiConfig;
import com.vn.jmixcamel.dto.DbLookupConfig;
import com.vn.jmixcamel.dto.ExecutionConfig;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ConfigSecurityValidator {

    private static final Set<String> ALLOWED_URL_SCHEMES = Set.of("http", "https");

    private static final Set<String> ALLOWED_LOOKUP_TYPES = Set.of(
            "customer",
            "order",
            "product"
    );

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "127.0.0.1",
            "0.0.0.0",
            "::1"
    );

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
        validateDbLookup(config.getDbLookup());
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
        if (BLOCKED_HOSTS.contains(host.toLowerCase())) {
            throw new IllegalArgumentException("Host not allowed: " + host);
        }
        if (host.startsWith("169.254.") || host.startsWith("10.") || host.startsWith("192.168.")) {
            throw new IllegalArgumentException("Private/internal host not allowed: " + host);
        }
    }

    private void validateDbLookup(DbLookupConfig dbLookup) {
        if (dbLookup == null) {
            return;
        }
        String type = dbLookup.getType();
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("config.dbLookup.type is required");
        }
        if (!ALLOWED_LOOKUP_TYPES.contains(type.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Lookup type not allowed: " + type +
                            ". Allowed: " + ALLOWED_LOOKUP_TYPES
            );
        }
        if (dbLookup.getBy() == null || dbLookup.getBy().isEmpty()) {
            throw new IllegalArgumentException("config.dbLookup.by is required");
        }
    }
}
