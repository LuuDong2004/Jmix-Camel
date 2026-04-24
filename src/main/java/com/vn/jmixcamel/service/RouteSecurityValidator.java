package com.vn.jmixcamel.service;



import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RouteSecurityValidator {
    private static final List<String> BLOCKED_KEYWORDS = List.of(
            "file:",
            "exec:",
            "bean:",
            "class:",
            "script:",
            "jdbc:",
            "jpa:",
            "ftp:",
            "sftp:",
            "ssh:",
            "docker:",
            "kubernetes:"
    );

    public void validate(String routeContent) {
        String lower = routeContent.toLowerCase();

        for (String keyword : BLOCKED_KEYWORDS) {
            if (lower.contains(keyword)) {
                throw new IllegalArgumentException("Blocked endpoint/component: " + keyword);
            }
        }

        if (!lower.contains("direct:")) {
            throw new IllegalArgumentException("Route must start from direct endpoint for manual execution");
        }
    }
}
