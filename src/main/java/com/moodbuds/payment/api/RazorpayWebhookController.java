package com.moodbuds.payment.api;

import com.moodbuds.payment.RazorpayPaymentService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/payments/razorpay/webhook")
public class RazorpayWebhookController {
    private final RazorpayPaymentService payments;

    public RazorpayWebhookController(RazorpayPaymentService payments) { this.payments = payments; }

    @PostMapping
    ResponseEntity<Void> receive(@RequestHeader("X-Razorpay-Signature") @NotBlank String signature,
                                 @RequestHeader("X-Razorpay-Event-Id") @NotBlank String eventId,
                                 @RequestBody String rawBody) {
        payments.processWebhook(eventId, signature, rawBody);
        return ResponseEntity.ok().build();
    }
}
