package com.moodbuds.admin;

import java.util.List;
import java.util.Map;

import com.moodbuds.audit.AuditService;
import com.moodbuds.common.ApiException;
import com.moodbuds.common.PageResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {
    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public AdminUserService(JdbcClient jdbc, PasswordEncoder passwordEncoder, AuditService audit) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    public PageResponse<Map<String, Object>> list(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        var rows = jdbc.sql("""
                SELECT au.id, au.username, au.email, au.full_name, au.mobile, au.is_active,
                       au.last_login_at, au.failed_attempts, au.locked_until, au.created_at, au.updated_at,
                       ar.id AS role_id, ar.name AS role_name
                FROM admin_users au JOIN admin_roles ar ON ar.id=au.role_id
                ORDER BY au.id DESC LIMIT :limit OFFSET :offset
                """).param("limit", safeSize).param("offset", safePage * safeSize).query().listOfRows();
        long total = jdbc.sql("SELECT COUNT(*) FROM admin_users").query(Long.class).single();
        return PageResponse.of(rows, safePage, safeSize, total);
    }

    public Map<String, Object> get(long id) {
        return jdbc.sql("""
                SELECT au.id, au.username, au.email, au.full_name, au.mobile, au.is_active,
                       au.last_login_at, au.failed_attempts, au.locked_until, au.created_at, au.updated_at,
                       ar.id AS role_id, ar.name AS role_name
                FROM admin_users au JOIN admin_roles ar ON ar.id=au.role_id WHERE au.id=:id
                """).param("id", id).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Administrator"));
    }

    @Transactional
    public Map<String, Object> create(CreateAdminRequest request, long actorId) {
        ensureRoleExists(request.roleId());
        jdbc.sql("""
                INSERT INTO admin_users(username, role_id, email, password_hash, full_name, mobile, is_active,
                                        require_2fa, failed_attempts, created_at, updated_at)
                VALUES (:username, :roleId, :email, :password, :fullName, :mobile, 1, 0, 0, UTC_TIMESTAMP(), UTC_TIMESTAMP())
                """).param("username", request.username().trim()).param("roleId", request.roleId())
                .param("email", blankToNull(request.email())).param("password", passwordEncoder.encode(request.password()))
                .param("fullName", request.fullName().trim()).param("mobile", blankToNull(request.mobile())).update();
        long id = jdbc.sql("SELECT id FROM admin_users WHERE username=:username").param("username", request.username().trim()).query(Long.class).single();
        var created = get(id);
        audit.record(actorId, "admin.created", "admin_user", id, null, created);
        return created;
    }

    @Transactional
    public Map<String, Object> update(long id, UpdateAdminRequest request, long actorId) {
        var old = get(id);
        if (request.roleId() != null) ensureRoleExists(request.roleId());
        jdbc.sql("""
                UPDATE admin_users SET
                  full_name=COALESCE(:fullName, full_name), role_id=COALESCE(:roleId, role_id),
                  email=COALESCE(:email, email), mobile=COALESCE(:mobile, mobile),
                  is_active=COALESCE(:active, is_active), updated_at=UTC_TIMESTAMP()
                WHERE id=:id
                """).param("fullName", blankToNull(request.fullName())).param("roleId", request.roleId())
                .param("email", blankToNull(request.email())).param("mobile", blankToNull(request.mobile()))
                .param("active", request.active()).param("id", id).update();
        var updated = get(id);
        audit.record(actorId, "admin.updated", "admin_user", id, old, updated);
        return updated;
    }

    @Transactional
    public void changePassword(long id, String password, long actorId) {
        if (password == null || password.length() < 10) throw ApiException.badRequest("WEAK_PASSWORD", "Password must contain at least 10 characters");
        if (jdbc.sql("UPDATE admin_users SET password_hash=:hash, failed_attempts=0, locked_until=NULL, updated_at=UTC_TIMESTAMP() WHERE id=:id")
                .param("hash", passwordEncoder.encode(password)).param("id", id).update() == 0) throw ApiException.notFound("Administrator");
        audit.record(actorId, "admin.password_changed", "admin_user", id, null, Map.of("passwordChanged", true));
    }

    @Transactional
    public void deactivate(long id, long actorId) {
        if (id == actorId) throw ApiException.badRequest("SELF_DEACTIVATION", "You cannot deactivate your own account");
        if (jdbc.sql("UPDATE admin_users SET is_active=0, updated_at=UTC_TIMESTAMP() WHERE id=:id").param("id", id).update() == 0)
            throw ApiException.notFound("Administrator");
        audit.record(actorId, "admin.deactivated", "admin_user", id, null, Map.of("active", false));
    }

    private void ensureRoleExists(long roleId) {
        if (jdbc.sql("SELECT COUNT(*) FROM admin_roles WHERE id=:id AND is_active=1").param("id", roleId).query(Integer.class).single() == 0)
            throw ApiException.badRequest("INVALID_ROLE", "The selected active role does not exist");
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
