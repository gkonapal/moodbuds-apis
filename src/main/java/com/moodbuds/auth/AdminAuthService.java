package com.moodbuds.auth;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;

import com.moodbuds.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AdminAuthService(JdbcClient jdbc, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        var row = jdbc.sql("""
                SELECT au.id, au.username, au.password_hash, au.full_name, au.is_active,
                       au.failed_attempts, au.locked_until, ar.name AS role_name, ar.is_active AS role_active
                FROM admin_users au
                JOIN admin_roles ar ON ar.id = au.role_id
                WHERE au.username = :username
                """).param("username", request.username().trim()).query((rs, n) -> new LoginRow(
                        rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"), rs.getString("full_name"),
                        rs.getBoolean("is_active"), rs.getInt("failed_attempts"), rs.getTimestamp("locked_until"),
                        rs.getString("role_name"), rs.getBoolean("role_active"))).optional()
                .orElseThrow(() -> invalidCredentials(request.username()));

        if (!row.active || !row.roleActive) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_DISABLED", "This administrator account is disabled");
        }
        if (row.lockedUntil != null && row.lockedUntil.toLocalDateTime().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new ApiException(HttpStatus.LOCKED, "ADMIN_LOCKED", "This administrator account is temporarily locked");
        }
        if (!passwordEncoder.matches(request.password(), row.passwordHash)) {
            registerFailure(row);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
        }

        var permissions = new LinkedHashSet<>(jdbc.sql("""
                SELECT ap.permission_key
                FROM admin_role_permissions arp
                JOIN admin_permissions ap ON ap.id = arp.permission_id
                JOIN admin_users au ON au.role_id = arp.role_id
                WHERE au.id = :id
                ORDER BY ap.permission_key
                """).param("id", row.id).query(String.class).list());
        jdbc.sql("UPDATE admin_users SET failed_attempts=0, locked_until=NULL, last_login_at=UTC_TIMESTAMP() WHERE id=:id")
                .param("id", row.id).update();
        var principal = new AdminPrincipal(row.id, row.username, row.roleName, permissions);
        var token = jwtService.issue(principal);
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(),
                new LoginResponse.AdminSummary(row.id, row.username, row.fullName, row.roleName, permissions));
    }

    private ApiException invalidCredentials(String username) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
    }

    private void registerFailure(LoginRow row) {
        int attempts = row.failedAttempts + 1;
        jdbc.sql("""
                UPDATE admin_users
                SET failed_attempts=:attempts,
                    locked_until=CASE WHEN :attempts >= :max THEN DATE_ADD(UTC_TIMESTAMP(), INTERVAL 15 MINUTE) ELSE NULL END
                WHERE id=:id
                """).param("attempts", attempts).param("max", MAX_FAILED_ATTEMPTS).param("id", row.id).update();
    }

    private record LoginRow(long id, String username, String passwordHash, String fullName, boolean active,
                            int failedAttempts, Timestamp lockedUntil, String roleName, boolean roleActive) {}
}
