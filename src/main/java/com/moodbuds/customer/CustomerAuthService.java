package com.moodbuds.customer;

import static com.moodbuds.customer.api.CustomerDtos.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import com.moodbuds.auth.JwtService;
import com.moodbuds.common.ApiException;
import com.moodbuds.config.SecurityProperties;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerAuthService {
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CustomerTokenService tokenService;
    private final SecurityProperties properties;

    public CustomerAuthService(JdbcClient jdbc, PasswordEncoder passwordEncoder, JwtService jwtService,
                               CustomerTokenService tokenService, SecurityProperties properties) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (exists("email", email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "An account already exists for this email");
        }
        String mobile = clean(request.mobile());
        if (mobile != null && exists("mobile", mobile)) {
            throw new ApiException(HttpStatus.CONFLICT, "MOBILE_ALREADY_REGISTERED", "An account already exists for this mobile number");
        }
        jdbc.sql("""
                INSERT INTO users(email,password_hash,mobile,mobile_verified,email_verified,first_name,last_name,
                                  gender,is_active,failed_login_attempts,created_at,updated_at)
                VALUES(:email,:password,:mobile,0,0,:firstName,:lastName,'UNSPECIFIED',1,0,CURRENT_TIMESTAMP(),CURRENT_TIMESTAMP())
                """).param("email", email).param("password", passwordEncoder.encode(request.password()))
                .param("mobile", mobile, java.sql.Types.VARCHAR)
                .param("firstName", request.firstName().trim()).param("lastName", request.lastName().trim()).update();
        var user = loadByEmail(email);
        return createSession(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(CustomerLoginRequest request) {
        String email = normalizeEmail(request.email());
        var user = jdbc.sql(userSql("WHERE email=:email FOR UPDATE")).param("email", email)
                .query((rs, rowNum) -> user(rs)).optional()
                .orElseThrow(CustomerAuthService::invalidCredentials);
        if (!user.active()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CUSTOMER_DISABLED", "This customer account is disabled");
        }
        if (user.lockedUntil() != null && user.lockedUntil().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.LOCKED, "CUSTOMER_LOCKED", "This account is temporarily locked");
        }
        if (user.passwordHash() == null || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            registerFailure(user);
            throw invalidCredentials();
        }
        jdbc.sql("""
                UPDATE users SET failed_login_attempts=0,locked_until=NULL,last_login_at=CURRENT_TIMESTAMP()
                WHERE id=:id
                """).param("id", user.id()).update();
        return createSession(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String hash = tokenService.hash(request.refreshToken());
        var session = jdbc.sql("""
                SELECT s.id session_id,s.user_id,s.expires_at,s.revoked_at,
                       u.id,u.email,u.password_hash,u.mobile,u.mobile_verified,u.email_verified,
                       u.first_name,u.last_name,u.date_of_birth,u.gender,u.is_active,
                       u.failed_login_attempts,u.locked_until,u.created_at,u.updated_at
                FROM customer_auth_sessions s
                JOIN users u ON u.id=s.user_id
                WHERE s.refresh_token_hash=:hash FOR UPDATE
                """).param("hash", hash).query((rs, rowNum) -> new RefreshSession(
                        rs.getString("session_id"), rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(), user(rs)))
                .optional().orElseThrow(CustomerAuthService::invalidRefreshToken);
        if (session.revokedAt() != null || !session.expiresAt().isAfter(Instant.now())) throw invalidRefreshToken();
        if (!session.user().active()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CUSTOMER_DISABLED", "This customer account is disabled");
        }
        var replacement = tokenService.create();
        jdbc.sql("""
                UPDATE customer_auth_sessions
                SET refresh_token_hash=:hash,last_used_at=CURRENT_TIMESTAMP()
                WHERE id=:id
                """).param("hash", replacement.hash()).param("id", session.id()).update();
        return response(session.user(), session.id(), replacement.raw(), session.expiresAt());
    }

    @Transactional
    public void logout(long customerId, String sessionId, LogoutRequest request) {
        var stored = jdbc.sql("""
                SELECT refresh_token_hash FROM customer_auth_sessions
                WHERE id=:sessionId AND user_id=:customerId AND revoked_at IS NULL FOR UPDATE
                """).param("sessionId", sessionId).param("customerId", customerId).query(String.class).optional()
                .orElseThrow(CustomerAuthService::invalidRefreshToken);
        if (!tokenService.matches(request.refreshToken(), stored)) throw invalidRefreshToken();
        jdbc.sql("UPDATE customer_auth_sessions SET revoked_at=CURRENT_TIMESTAMP() WHERE id=:id")
                .param("id", sessionId).update();
    }

    public void logoutAll(long customerId) {
        jdbc.sql("""
                UPDATE customer_auth_sessions SET revoked_at=CURRENT_TIMESTAMP()
                WHERE user_id=:userId AND revoked_at IS NULL
                """).param("userId", customerId).update();
    }

    private AuthResponse createSession(UserRow user) {
        String sessionId = UUID.randomUUID().toString();
        var refresh = tokenService.create();
        Instant refreshExpiresAt = Instant.now().plus(properties.customerRefreshTokenValidity());
        jdbc.sql("""
                INSERT INTO customer_auth_sessions(id,user_id,refresh_token_hash,expires_at,created_at)
                VALUES(:id,:userId,:hash,:expiresAt,CURRENT_TIMESTAMP())
                """).param("id", sessionId).param("userId", user.id()).param("hash", refresh.hash())
                .param("expiresAt", LocalDateTime.ofInstant(refreshExpiresAt, ZoneOffset.UTC)).update();
        return response(user, sessionId, refresh.raw(), refreshExpiresAt);
    }

    private AuthResponse response(UserRow user, String sessionId, String refreshToken, Instant refreshExpiresAt) {
        var access = jwtService.issueCustomer(user.id(), user.email(), sessionId);
        return new AuthResponse(access.value(), refreshToken, "Bearer", access.expiresAt(), refreshExpiresAt, profile(user));
    }

    private void registerFailure(UserRow user) {
        int failures = user.failedAttempts() + 1;
        jdbc.sql("""
                UPDATE users SET failed_login_attempts=:failures,
                    locked_until=CASE WHEN :failures>=:max THEN DATEADD('MINUTE', 15, CURRENT_TIMESTAMP()) ELSE NULL END
                WHERE id=:id
                """).param("failures", failures).param("max", MAX_FAILED_ATTEMPTS)
                .param("id", user.id()).update();
    }

    private boolean exists(String column, String value) {
        return jdbc.sql("SELECT COUNT(*) FROM users WHERE " + column + "=:value")
                .param("value", value).query(Integer.class).single() > 0;
    }

    UserRow load(long id) {
        return jdbc.sql(userSql("WHERE id=:id")).param("id", id).query((rs, rowNum) -> user(rs)).optional()
                .orElseThrow(() -> ApiException.notFound("Customer"));
    }

    private UserRow loadByEmail(String email) {
        return jdbc.sql(userSql("WHERE email=:email")).param("email", email).query((rs, rowNum) -> user(rs)).optional()
                .orElseThrow(() -> ApiException.notFound("Customer"));
    }

    static CustomerProfile profile(UserRow user) {
        return new CustomerProfile(user.id(), user.email(), user.emailVerified(), user.mobile(), user.mobileVerified(),
                user.firstName(), user.lastName(), user.dateOfBirth(), user.gender(), user.active(),
                user.createdAt(), user.updatedAt());
    }

    private static String userSql(String where) {
        return """
                SELECT id,email,password_hash,mobile,mobile_verified,email_verified,first_name,last_name,
                       date_of_birth,gender,is_active,failed_login_attempts,locked_until,created_at,updated_at
                FROM users
                """ + where;
    }

    private static UserRow user(ResultSet rs) throws SQLException {
        Timestamp locked = rs.getTimestamp("locked_until");
        return new UserRow(rs.getLong("id"), rs.getString("email"), rs.getString("password_hash"),
                rs.getString("mobile"), rs.getBoolean("mobile_verified"), rs.getBoolean("email_verified"),
                rs.getString("first_name"), rs.getString("last_name"),
                rs.getObject("date_of_birth", java.time.LocalDate.class), Gender.valueOf(rs.getString("gender")),
                rs.getBoolean("is_active"), rs.getInt("failed_login_attempts"),
                locked == null ? null : locked.toInstant(), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
    }
    private static ApiException invalidRefreshToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "The refresh token is invalid or expired");
    }

    record UserRow(long id, String email, String passwordHash, String mobile, boolean mobileVerified,
                   boolean emailVerified, String firstName, String lastName, java.time.LocalDate dateOfBirth,
                   Gender gender, boolean active, int failedAttempts, Instant lockedUntil,
                   Instant createdAt, Instant updatedAt) {}
    private record RefreshSession(String id, Instant expiresAt, Instant revokedAt, UserRow user) {}
}
