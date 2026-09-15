package com.moodbuds.tools;

import java.sql.DriverManager;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Creates or resets the first application-level SUPER_ADMIN without storing a
 * plaintext password in source code or a Flyway migration.
 */
public final class SuperAdminBootstrapTool {
    private SuperAdminBootstrapTool() {}

    public static void main(String[] args) throws Exception {
        String username = required("MOODBUDS_ADMIN_USERNAME");
        String password = required("MOODBUDS_ADMIN_PASSWORD");
        if (password.length() < 12) {
            throw new IllegalArgumentException("MOODBUDS_ADMIN_PASSWORD must contain at least 12 characters");
        }

        String host = environment("MOODBUDS_DB_HOST", "localhost");
        String port = environment("MOODBUDS_DB_PORT", "3306");
        String database = environment("MOODBUDS_DB_NAME", "mb");
        String databaseUsername = environment("MOODBUDS_DB_USERNAME", "root");
        String databasePassword = required("MOODBUDS_DB_PASSWORD");
        String fullName = environment("MOODBUDS_ADMIN_FULL_NAME", "MoodBuds Super Admin");
        String email = nullableEnvironment("MOODBUDS_ADMIN_EMAIL");
        String passwordHash = new BCryptPasswordEncoder(12).encode(password);

        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        String sql = """
                INSERT INTO admin_users
                    (username, role_id, email, password_hash, full_name, mobile, is_active,
                     require_2fa, failed_attempts, locked_until, created_at, updated_at)
                SELECT ?, id, ?, ?, ?, NULL, 1, 0, 0, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP()
                FROM admin_roles
                WHERE name = 'SUPER_ADMIN' AND is_active = 1
                ON DUPLICATE KEY UPDATE
                    role_id = VALUES(role_id),
                    email = VALUES(email),
                    password_hash = VALUES(password_hash),
                    full_name = VALUES(full_name),
                    is_active = 1,
                    require_2fa = 0,
                    failed_attempts = 0,
                    locked_until = NULL,
                    updated_at = UTC_TIMESTAMP()
                """;

        try (var connection = DriverManager.getConnection(jdbcUrl, databaseUsername, databasePassword);
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            statement.setString(4, fullName);
            int affectedRows = statement.executeUpdate();
            if (affectedRows == 0) {
                throw new IllegalStateException("Active SUPER_ADMIN role was not found");
            }
        }

        System.out.println("SUPER_ADMIN account is ready for username: " + username);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Set " + name);
        }
        return value.trim();
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String nullableEnvironment(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
