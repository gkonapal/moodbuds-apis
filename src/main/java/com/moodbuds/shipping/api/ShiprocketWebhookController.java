package com.moodbuds.shipping.api;

import com.moodbuds.shipping.ShippingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shipping/shiprocket")
public class ShiprocketWebhookController {
    private final ShippingService shipping;

    public ShiprocketWebhookController(ShippingService shipping) { this.shipping = shipping; }

    @PostMapping("/webhook")
    ResponseEntity<Void> webhook(@RequestHeader(name = "X-Shiprocket-Token", required = false) String token,
                                 @RequestBody String body) {
        shipping.processWebhook(token, body);
        return ResponseEntity.ok().build();
    }
}
