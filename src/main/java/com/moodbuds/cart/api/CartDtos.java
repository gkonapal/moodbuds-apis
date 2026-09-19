package com.moodbuds.cart.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CartDtos {
    private CartDtos() {}

    public static final String SIZE_PATTERN = "^(XS|S|M|L|XL|XXL|3XL)$";

    public record AddCartItemRequest(
            @NotBlank @Size(max = 360) String productSlug,
            @NotBlank @Pattern(regexp = SIZE_PATTERN, message = "must be one of XS, S, M, L, XL, XXL, 3XL") String size,
            @NotNull @Min(1) @Max(10) Integer quantity) {}

    public record UpdateCartItemRequest(
            @Pattern(regexp = SIZE_PATTERN, message = "must be one of XS, S, M, L, XL, XXL, 3XL") String size,
            @Min(1) @Max(10) Integer quantity) {}

    public record CartResponse(Long cartId, int distinctItemCount, int totalQuantity,
                               List<CartItemResponse> items, CartTotals totals, Instant updatedAt) {}

    public record CartItemResponse(long id, long productId, String sku, String productSlug, String productName,
                                   String primaryImageUrl, String size, int quantity,
                                   long addedUnitPrice, Long addedUnitDiscountPrice,
                                   long currentUnitPrice, Long currentUnitDiscountPrice, long effectiveUnitPrice,
                                   boolean priceChanged, BigDecimal gstRatePercentage, long gstAmount,
                                   long lineSubtotal, long lineTotal, boolean productAvailable,
                                   boolean sizeConfigured, boolean requestedQuantityAvailable) {}

    public record CartTotals(long mrpSubtotal, long productDiscount, long sellingSubtotal,
                             long gstAmount, long grandTotal) {}

    public record CartCountResponse(int distinctItemCount, int totalQuantity) {}

    public record CartIssue(long itemId, String productSlug, String code, String message, boolean blocking) {}

    public record CartValidationResponse(boolean valid, boolean pricesRefreshed,
                                         List<CartIssue> issues, CartResponse cart) {}
}
