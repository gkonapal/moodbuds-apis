package com.moodbuds.cart.api;

import static com.moodbuds.cart.api.CartDtos.*;

import com.moodbuds.cart.CartService;
import com.moodbuds.customer.CurrentCustomer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer/cart")
@Tag(name = "Customer Cart")
public class CartController {
    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping
    @Operation(summary = "Get the authenticated customer's persistent cart")
    CartResponse get(@AuthenticationPrincipal Jwt jwt) {
        return cart.get(CurrentCustomer.id(jwt));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get checkout-facing cart items and price totals")
    CartResponse summary(@AuthenticationPrincipal Jwt jwt) {
        return cart.summary(CurrentCustomer.id(jwt));
    }

    @GetMapping("/count")
    @Operation(summary = "Get distinct item and total quantity counts")
    CartCountResponse count(@AuthenticationPrincipal Jwt jwt) {
        return cart.count(CurrentCustomer.id(jwt));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Idempotently add a product and size to the cart")
    CartResponse add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddCartItemRequest request) {
        return cart.add(CurrentCustomer.id(jwt), request);
    }

    @PatchMapping("/items/{itemId}")
    @Operation(summary = "Update cart item quantity, size, or both")
    CartResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId,
                        @Valid @RequestBody UpdateCartItemRequest request) {
        return cart.update(CurrentCustomer.id(jwt), itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Remove a customer-owned cart item")
    CartResponse remove(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId) {
        return cart.remove(CurrentCustomer.id(jwt), itemId);
    }

    @PostMapping("/items/{itemId}/move-to-wishlist")
    @Operation(summary = "Move a customer-owned cart item to the wishlist")
    MoveToWishlistResponse moveToWishlist(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId) {
        return cart.moveToWishlist(CurrentCustomer.id(jwt), itemId);
    }

    @DeleteMapping
    @Operation(summary = "Clear all cart items and any pending coupon")
    CartResponse clear(@AuthenticationPrincipal Jwt jwt) {
        return cart.clear(CurrentCustomer.id(jwt));
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate availability and refresh changed prices")
    CartValidationResponse validate(@AuthenticationPrincipal Jwt jwt) {
        return cart.validate(CurrentCustomer.id(jwt));
    }
}
