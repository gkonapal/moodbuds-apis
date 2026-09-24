package com.moodbuds.shipping.api;

import com.moodbuds.common.PageResponse;
import com.moodbuds.shipping.ShippingDtos.MockShipmentUpdateRequest;
import com.moodbuds.shipping.ShippingDtos.ShipmentResponse;
import com.moodbuds.shipping.ShippingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/shipments")
public class ShippingAdminController {
    private final ShippingService shipping;

    public ShippingAdminController(ShippingService shipping) { this.shipping = shipping; }

    @GetMapping
    @PreAuthorize("hasAuthority('orders.read') or hasRole('SUPER_ADMIN')")
    PageResponse<ShipmentResponse> list(@RequestParam(required = false) String direction,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "100") int size) {
        return shipping.list(direction, status, page, size);
    }

    @PostMapping("/orders/{orderNumber}/book")
    @PreAuthorize("hasAuthority('orders.manage') or hasRole('SUPER_ADMIN')")
    ShipmentResponse book(@PathVariable String orderNumber) {
        return shipping.bookForwardByOrderNumber(orderNumber);
    }

    @PatchMapping("/{shipmentId}/mock")
    @PreAuthorize("hasAuthority('orders.manage') or hasRole('SUPER_ADMIN')")
    ShipmentResponse mock(@PathVariable long shipmentId, @RequestBody MockShipmentUpdateRequest request) {
        return shipping.mockUpdate(shipmentId, request);
    }
}
