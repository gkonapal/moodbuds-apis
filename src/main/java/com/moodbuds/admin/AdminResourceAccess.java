package com.moodbuds.admin;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("adminResourceAccess")
public class AdminResourceAccess {
    public boolean canRead(String resource, Authentication authentication) {
        return isSuperAdmin(authentication) || switch (resource) {
            case "coupons" -> has(authentication, "coupons.manage");
            case "moods" -> has(authentication, "catalog.read") || has(authentication, "moods.manage");
            default -> has(authentication, "catalog.read");
        };
    }

    public boolean canManage(String resource, Authentication authentication) {
        return isSuperAdmin(authentication) || switch (resource) {
            case "coupons" -> has(authentication, "coupons.manage");
            case "moods" -> has(authentication, "moods.manage");
            default -> has(authentication, "catalog.manage");
        };
    }

    private boolean isSuperAdmin(Authentication authentication) {
        return has(authentication, "ROLE_SUPER_ADMIN");
    }

    private boolean has(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().equals(authority));
    }
}
