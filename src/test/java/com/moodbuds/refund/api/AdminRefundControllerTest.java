package com.moodbuds.refund.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.moodbuds.audit.AuditService;
import com.moodbuds.refund.RefundService;
import com.moodbuds.refund.RefundStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminRefundController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminRefundControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean RefundService refunds;
    @MockBean RefundStore store;
    @MockBean AuditService audit;

    @Test
    void initiatesServerCalculatedRefundWithIdempotencyKey() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("adminId")).thenReturn(4L);
        var request = new RefundDtos.InitiateRefundRequest(RefundDtos.RefundSpeed.OPTIMUM);
        when(refunds.initiate(8L, "refund-key-123", request)).thenReturn(response());

        new AdminRefundController(refunds, store, audit).initiate(8L, "refund-key-123", request, jwt);

        verify(refunds).initiate(8L, "refund-key-123", request);
        verify(audit).record(eq(4L), eq("refund.initiated"), eq("refund"), eq(11L), eq(null),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsShortIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/admin/returns/8/refunds")
                        .header("Idempotency-Key", "short")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"speed\":\"OPTIMUM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_REQUEST"));
    }

    private RefundDtos.RefundResponse response() {
        Instant now = Instant.parse("2026-09-05T06:00:00Z");
        return new RefundDtos.RefundResponse(11, "MB-1", 8, "rfnd_test", 5_250, "INR",
                RefundDtos.RefundStatus.INITIATED, RefundDtos.RefundSpeed.OPTIMUM, "normal",
                null, null, null, 1, now, now, null, now);
    }
}
