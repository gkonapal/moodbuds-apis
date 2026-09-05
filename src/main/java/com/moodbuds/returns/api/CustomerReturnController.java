package com.moodbuds.returns.api;

import static com.moodbuds.returns.api.ReturnDtos.*;

import com.moodbuds.common.PageResponse;
import com.moodbuds.customer.CurrentCustomer;
import com.moodbuds.returns.CustomerReturnService;
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
@RequestMapping("/api/v1/customer")
@Tag(name = "Customer Returns")
public class CustomerReturnController {
    private final CustomerReturnService returns;

    public CustomerReturnController(CustomerReturnService returns) {
        this.returns = returns;
    }

    @GetMapping("/orders/{orderNumber}/return-eligibility")
    @Operation(summary = "Get delivered-order item return eligibility and remaining quantities")
    ReturnEligibilityResponse eligibility(@AuthenticationPrincipal Jwt jwt, @PathVariable String orderNumber) {
        return returns.eligibility(CurrentCustomer.id(jwt), orderNumber);
    }

    @PostMapping("/orders/{orderNumber}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an idempotent return request for delivered order items")
    ReturnDetailResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable String orderNumber,
                                @RequestHeader("Idempotency-Key")
                                @NotBlank @Size(min = 8, max = 128) String idempotencyKey,
                                @Valid @RequestBody CreateReturnRequest request) {
        return returns.create(CurrentCustomer.id(jwt), orderNumber, idempotencyKey, request);
    }

    @GetMapping("/returns")
    @Operation(summary = "List the authenticated customer's return requests")
    PageResponse<ReturnSummaryResponse> list(@AuthenticationPrincipal Jwt jwt,
                                             @RequestParam(required = false) ReturnStatus status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return returns.list(CurrentCustomer.id(jwt), status, page, size);
    }

    @GetMapping("/returns/{returnId}")
    @Operation(summary = "Get a customer-owned return request with its items")
    ReturnDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long returnId) {
        return returns.get(CurrentCustomer.id(jwt), returnId);
    }
}
