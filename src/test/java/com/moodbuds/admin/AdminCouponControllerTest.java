package com.moodbuds.admin;

import static com.moodbuds.admin.AdminCouponDtos.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminCouponController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminCouponControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean AdminCouponService service;

    @BeforeEach void authenticate(){
        Jwt jwt=Jwt.withTokenValue("test").header("alg","none").claim("adminId",7L)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test void createsFirstOrderHomepageCoupon() throws Exception {
        Instant now=Instant.parse("2026-09-15T10:00:00Z");
        var response=new CouponResponse(4,"MOOD300","Welcome",CouponType.FLAT,BigDecimal.valueOf(300),
                149900L,null,null,1,0,AudienceType.PUBLIC,true,true,true,"ACTIVE",now,null,0,0,now,now);
        when(service.create(any(CouponRequest.class),eq(7L))).thenReturn(response);
        mockMvc.perform(post("/api/v1/admin/coupons").contentType(MediaType.APPLICATION_JSON).content("""
                {"code":"MOOD300","description":"Welcome","type":"FLAT","discountValue":300,
                 "minOrderValue":149900,"usageLimitPerUser":1,"audienceType":"PUBLIC",
                 "firstOrderOnly":true,"showOnHomepage":true,"active":true,
                 "validFrom":"2026-09-15T10:00:00Z","assignedUserIds":[]}
                """))
                .andExpect(status().isCreated()).andExpect(header().string("Location","/api/v1/admin/coupons/4"))
                .andExpect(jsonPath("$.firstOrderOnly").value(true)).andExpect(jsonPath("$.usageLimitPerUser").value(1));
    }

    @Test void validatesRequiredCouponFields() throws Exception {
        mockMvc.perform(post("/api/v1/admin/coupons").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }

    @Test void activatesAndDeactivatesWithoutDeleting() throws Exception {
        Instant now=Instant.now();
        when(service.status(4,false,7)).thenReturn(new CouponResponse(4,"MOOD300",null,CouponType.FLAT,
                BigDecimal.valueOf(300),0,null,null,1,0,AudienceType.PUBLIC,true,true,false,"INACTIVE",now,null,0,0,now,now));
        mockMvc.perform(patch("/api/v1/admin/coupons/4/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }
}
