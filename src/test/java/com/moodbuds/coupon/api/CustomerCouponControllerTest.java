package com.moodbuds.coupon.api;

import static com.moodbuds.coupon.api.CouponDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moodbuds.coupon.CouponService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerCouponController.class)
@AutoConfigureMockMvc(addFilters = false)
class CustomerCouponControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean CouponService coupons;

    @Test
    void usesAuthenticatedCustomerWhenRemovingCoupon() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("customerId")).thenReturn(9L);
        when(coupons.remove(9L)).thenReturn(new CartCouponResponse(false, null, null, null));

        assertThat(new CustomerCouponController(coupons).remove(jwt).applied()).isFalse();
    }

    @Test
    void rejectsBlankCouponCode() throws Exception {
        mockMvc.perform(post("/api/v1/customer/cart/coupon").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }
}
