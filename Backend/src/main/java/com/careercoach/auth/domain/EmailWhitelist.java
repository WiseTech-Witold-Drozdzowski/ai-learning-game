package com.careercoach.auth.domain;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "careercoach.auth")
public record EmailWhitelist(List<String> whitelist) {

    public boolean permits(String email) {
        if (email == null || email.isBlank() || whitelist == null) {
            return false;
        }
        return whitelist.stream()
                .filter(allowed -> allowed != null && !allowed.isBlank())
                .anyMatch(allowed -> allowed.trim().equalsIgnoreCase(email.trim()));
    }
}
