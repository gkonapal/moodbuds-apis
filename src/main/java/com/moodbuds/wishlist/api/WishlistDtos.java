package com.moodbuds.wishlist.api;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class WishlistDtos {
    private WishlistDtos() {}

    public static final String SIZE_PATTERN = "^(XS|S|M|L|XL|XXL|3XL)$";

    public record AddWishlistItemRequest(
            @NotBlank @Size(max = 360) String productSlug,
            @Pattern(regexp = SIZE_PATTERN, message = "must be one of XS, S, M, L, XL, XXL, 3XL") String size) {}

    public record MoveToCartRequest(
            @Pattern(regexp = SIZE_PATTERN, message = "must be one of XS, S, M, L, XL, XXL, 3XL") String size,
            @Min(1) @Max(10) Integer quantity) {
        public MoveToCartRequest { quantity = quantity == null ? 1 : quantity; }
    }

    public record WishlistResponse(Long wishlistId, int itemCount, List<WishlistItemResponse> items) {}

    public record WishlistItemResponse(long id, long productId, String sku, String productSlug,
                                       String productName, String selectedSize, long price, Long discountPrice,
                                       long effectivePrice, String primaryImageUrl, boolean productAvailable,
                                       boolean selectedSizeAvailable, Instant addedAt) {}

    public record WishlistStatusResponse(String productSlug, String size, boolean wishlisted, Long wishlistItemId) {}

    public record MoveToCartResponse(long cartId, long cartItemId, String productSlug,
                                     String size, int quantity, boolean removedFromWishlist) {}
}
