package com.moodbuds.tools;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class AdminPasswordHashTool {
    private AdminPasswordHashTool() {}

    public static void main(String[] args) {
        String password = System.getenv("MOODBUDS_ADMIN_PASSWORD_TO_HASH");
        if (password == null || password.length() < 10) {
            throw new IllegalArgumentException("Set MOODBUDS_ADMIN_PASSWORD_TO_HASH to a password containing at least 10 characters");
        }
        System.out.println(new BCryptPasswordEncoder(12).encode(password));
    }
}
