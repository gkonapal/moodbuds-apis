package com.moodbuds.wishlist.api;

import static com.moodbuds.wishlist.api.WishlistDtos.*;

import com.moodbuds.customer.CurrentCustomer;
import com.moodbuds.wishlist.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer/wishlist")
@Tag(name = "Customer Wishlist")
@Validated
public class WishlistController {
    private final WishlistService wishlist;

    public WishlistController(WishlistService wishlist) {
        this.wishlist = wishlist;
    }

    @GetMapping
    @Operation(summary = "Get the authenticated customer's wishlist")
    WishlistResponse get(@AuthenticationPrincipal Jwt jwt) {
        return wishlist.get(CurrentCustomer.id(jwt));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Idempotently add a product and optional size to the wishlist")
    WishlistItemResponse add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddWishlistItemRequest request) {
        return wishlist.add(CurrentCustomer.id(jwt), request);
    }

    @GetMapping("/status")
    @Operation(summary = "Check whether a product and optional size is wishlisted")
    WishlistStatusResponse status(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @NotBlank @Size(max = 360) String productSlug,
            @RequestParam(required = false)
            @Pattern(regexp = SIZE_PATTERN, message = "must be one of XS, S, M, L, XL, XXL, 3XL") String size) {
        return wishlist.status(CurrentCustomer.id(jwt), productSlug, size);
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a customer-owned wishlist item")
    void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId) {
        wishlist.remove(CurrentCustomer.id(jwt), itemId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Clear the authenticated customer's wishlist")
    void clear(@AuthenticationPrincipal Jwt jwt) {
        wishlist.clear(CurrentCustomer.id(jwt));
    }

    @PostMapping("/items/{itemId}/move-to-cart")
    @Operation(summary = "Move a wishlist item into the authenticated customer's cart")
    MoveToCartResponse moveToCart(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId,
                                  @Valid @RequestBody MoveToCartRequest request) {
        return wishlist.moveToCart(CurrentCustomer.id(jwt), itemId, request);
    }
}
