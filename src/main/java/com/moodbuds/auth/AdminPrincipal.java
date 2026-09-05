package com.moodbuds.auth;

import java.util.Set;

public record AdminPrincipal(long id, String username, String role, Set<String> authorities) {}
