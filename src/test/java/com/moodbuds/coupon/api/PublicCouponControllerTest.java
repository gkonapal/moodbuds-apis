package com.moodbuds.coupon.api;

import static com.moodbuds.coupon.api.CouponDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;

import com.moodbuds.coupon.CouponService;
import org.junit.jupiter.api.Test;

class PublicCouponControllerTest {
    @Test void exposesConfiguredFirstOrderHomepageCouponToVisitors() {
        CouponService service=mock(CouponService.class);
        var offer=new CustomerCouponResponse("MOOD300","Welcome",CouponType.FLAT,BigDecimal.valueOf(300),
                149900,null,Instant.now(),null,CouponAudience.PUBLIC,true,true,1);
        when(service.publicHomepageCoupon()).thenReturn(offer);
        assertThat(new PublicCouponController(service).homepage().code()).isEqualTo("MOOD300");
    }
}
