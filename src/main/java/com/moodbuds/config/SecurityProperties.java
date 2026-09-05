package com.moodbuds.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moodbuds.security")
public record SecurityProperties(String jwtSecret, Duration tokenValidity, Duration customerAccessTokenValidity,
                                 Duration customerRefreshTokenValidity, String allowedOrigins) {
    public SecurityProperties {
        tokenValidity = tokenValidity == null ? Duration.ofHours(8) : tokenValidity;
        customerAccessTokenValidity = customerAccessTokenValidity == null ? Duration.ofMinutes(15) : customerAccessTokenValidity;
        customerRefreshTokenValidity = customerRefreshTokenValidity == null ? Duration.ofDays(30) : customerRefreshTokenValidity;
    }

    public List<String> allowedOriginList() {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return List.of();
        }
        return Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }
}
