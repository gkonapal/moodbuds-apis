package com.moodbuds.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class AdminCouponDtos {
    private AdminCouponDtos() {}

    public enum CouponType { FLAT, PERCENTAGE }
    public enum AudienceType { PUBLIC, PRIVATE_CODE, ASSIGNED_USERS }

    public record CouponRequest(
            @NotBlank @Size(max = 50) String code,
            @Size(max = 500) String description,
            @NotNull CouponType type,
            @NotNull @DecimalMin(value = "0.01") BigDecimal discountValue,
            long minOrderValue,
            Long maxDiscountAmount,
            Integer usageLimitGlobal,
            @Positive int usageLimitPerUser,
            @NotNull AudienceType audienceType,
            boolean firstOrderOnly,
            boolean showOnHomepage,
            boolean active,
            @NotNull Instant validFrom,
            Instant validUntil,
            List<Long> assignedUserIds) {}

    public record CouponResponse(
            long id, String code, String description, CouponType type, BigDecimal discountValue,
            long minOrderValue, Long maxDiscountAmount, Integer usageLimitGlobal,
            int usageLimitPerUser, int currentUsageCount, AudienceType audienceType,
            boolean firstOrderOnly, boolean showOnHomepage, boolean active,
            String status, Instant validFrom, Instant validUntil, int assignedUserCount,
            long totalDiscountGiven, Instant createdAt, Instant updatedAt) {}

    public record CouponStats(long activeCoupons, long redemptionsLast30Days,
                              long discountLast30Days, String topCode) {}

    public record AssignmentRequest(
            @Positive long userId,
            Integer usageLimitOverride,
            @Size(max = 255) String assignedReason,
            Long refundId,
            boolean active) {}

    public record AssignmentResponse(long id, long couponId, long userId, String customerEmail,
                                     String customerName, Integer usageLimitOverride, int usageCount,
                                     boolean active, String assignedReason, Long refundId,
                                     long assignedBy, Instant assignedAt, Instant updatedAt) {}
}
