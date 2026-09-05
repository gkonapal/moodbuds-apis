package com.moodbuds.auth;

import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentAdmin {
    private CurrentAdmin() {}

    public static long id(Jwt jwt) {
        Number id = jwt.getClaim("adminId");
        return id.longValue();
    }
}
