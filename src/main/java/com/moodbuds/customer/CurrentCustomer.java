package com.moodbuds.customer;

import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentCustomer {
    private CurrentCustomer() {}

    public static long id(Jwt jwt) {
        Number id = jwt.getClaim("customerId");
        return id.longValue();
    }

    public static String sessionId(Jwt jwt) {
        return jwt.getClaimAsString("sessionId");
    }
}
