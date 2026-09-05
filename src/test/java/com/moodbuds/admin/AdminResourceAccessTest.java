package com.moodbuds.admin;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminResourceAccessTest {
    private final AdminResourceAccess access = new AdminResourceAccess();

    @Test
    void couponManagerCanManageCouponsButNotProducts() {
        var authentication = new TestingAuthenticationToken("coupon-admin", null,
                List.of(new SimpleGrantedAuthority("coupons.manage")));
        assertThat(access.canManage("coupons", authentication)).isTrue();
        assertThat(access.canManage("products", authentication)).isFalse();
    }

    @Test
    void superAdminCanManageEveryRegisteredResource() {
        var authentication = new TestingAuthenticationToken("owner", null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        assertThat(access.canManage("products", authentication)).isTrue();
        assertThat(access.canManage("coupons", authentication)).isTrue();
        assertThat(access.canManage("moods", authentication)).isTrue();
    }
}
