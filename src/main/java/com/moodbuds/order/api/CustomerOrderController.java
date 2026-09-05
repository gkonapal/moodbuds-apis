package com.moodbuds.order.api;

import static com.moodbuds.order.api.OrderDtos.*;

import com.moodbuds.common.PageResponse;
import com.moodbuds.customer.CurrentCustomer;
import com.moodbuds.order.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/customer/orders")
@Tag(name = "Customer Orders")
public class CustomerOrderController {
    private final OrderService orders;

    public CustomerOrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place a provider-pending order from the authenticated customer's cart")
    OrderDetailResponse place(@AuthenticationPrincipal Jwt jwt,
                              @RequestHeader("Idempotency-Key") @NotBlank @Size(min = 8, max = 128) String key,
                              @Valid @RequestBody PlaceOrderRequest request) {
        return orders.place(CurrentCustomer.id(jwt), key, request);
    }

    @GetMapping
    @Operation(summary = "List the authenticated customer's orders")
    PageResponse<OrderSummaryResponse> list(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(required = false) OrderStatus status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return orders.list(CurrentCustomer.id(jwt), status, page, size);
    }

    @GetMapping("/{orderNumber}")
    @Operation(summary = "Get a customer-owned order with items, status history, and safe payment summaries")
    OrderDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String orderNumber) {
        return orders.get(CurrentCustomer.id(jwt), orderNumber);
    }
}
