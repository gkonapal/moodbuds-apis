package com.moodbuds.admin;

import java.util.List;
import java.util.Map;

import com.moodbuds.audit.AuditService;
import com.moodbuds.auth.CurrentAdmin;
import com.moodbuds.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class RbacController {
    private final JdbcClient jdbc;
    private final AuditService audit;
    public RbacController(JdbcClient jdbc, AuditService audit) { this.jdbc=jdbc; this.audit=audit; }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('roles.read') or hasRole('SUPER_ADMIN')")
    List<Map<String,Object>> roles() { return jdbc.sql("SELECT * FROM admin_roles ORDER BY name").query().listOfRows(); }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('roles.read') or hasRole('SUPER_ADMIN')")
    List<Map<String,Object>> permissions() { return jdbc.sql("SELECT * FROM admin_permissions ORDER BY module, permission_key").query().listOfRows(); }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('roles.manage') or hasRole('SUPER_ADMIN')")
    @Transactional
    Map<String,Object> createRole(@RequestBody Map<String,Object> body, @AuthenticationPrincipal Jwt jwt) {
        String name=String.valueOf(body.getOrDefault("name","")).trim().toUpperCase();
        if (name.isBlank()) throw ApiException.badRequest("ROLE_NAME_REQUIRED","Role name is required");
        jdbc.sql("INSERT INTO admin_roles(name,description,is_active,created_at) VALUES(:name,:description,1,UTC_TIMESTAMP())")
                .param("name",name).param("description",body.get("description")).update();
        var role=jdbc.sql("SELECT * FROM admin_roles WHERE name=:name").param("name",name).query().singleRow();
        audit.record(CurrentAdmin.id(jwt),"role.created","admin_role",role.get("id"),null,role);
        return role;
    }

    @PutMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('roles.manage') or hasRole('SUPER_ADMIN')")
    @Transactional
    void permissions(@PathVariable long roleId, @RequestBody PermissionAssignment body, @AuthenticationPrincipal Jwt jwt) {
        if (jdbc.sql("SELECT COUNT(*) FROM admin_roles WHERE id=:id").param("id",roleId).query(Integer.class).single()==0) throw ApiException.notFound("Role");
        jdbc.sql("DELETE FROM admin_role_permissions WHERE role_id=:id").param("id",roleId).update();
        for (Long permissionId : body.permissionIds()) {
            jdbc.sql("INSERT INTO admin_role_permissions(role_id,permission_id) VALUES(:role,:permission)")
                    .param("role",roleId).param("permission",permissionId).update();
        }
        audit.record(CurrentAdmin.id(jwt),"role.permissions_updated","admin_role",roleId,null,body);
    }

    public record PermissionAssignment(List<Long> permissionIds) {
        public PermissionAssignment { permissionIds = permissionIds == null ? List.of() : List.copyOf(permissionIds); }
    }
}
