package com.moodbuds.coupon.api;

import static com.moodbuds.coupon.api.CouponDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.moodbuds.coupon.CouponService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CustomerCouponCatalogControllerTest {
    @Test void homepageIsResolvedForAuthenticatedCustomerOnly() {
        CouponService service=mock(CouponService.class); Jwt jwt=mock(Jwt.class);
        when(jwt.getClaim("customerId")).thenReturn(9L);
        var coupon=new CustomerCouponResponse("MOOD300","Welcome",CouponType.FLAT,BigDecimal.valueOf(300),
                149900,null,Instant.now(),null,CouponAudience.PUBLIC,true,true,1);
        when(service.customerCoupons(9L,true)).thenReturn(List.of(coupon));
        assertThat(new CustomerCouponCatalogController(service).homepage(jwt).code()).isEqualTo("MOOD300");
    }
}
