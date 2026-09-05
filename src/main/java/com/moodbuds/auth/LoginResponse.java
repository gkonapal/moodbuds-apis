package com.moodbuds.auth;

import java.time.Instant;
import java.util.Set;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, AdminSummary admin) {
    public record AdminSummary(long id, String username, String fullName, String role, Set<String> permissions) {}
}
