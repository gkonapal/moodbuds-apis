package com.moodbuds.coupon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.ArrayList;

import com.moodbuds.cart.CartCalculations;

public final class CouponCalculations {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private CouponCalculations() {}

    public static long discount(String type, BigDecimal value, long subtotalPaise, Long maximumPaise) {
        if (subtotalPaise <= 0 || value == null || value.signum() <= 0) return 0;
        long calculated = switch (type) {
            case "FLAT" -> value.multiply(ONE_HUNDRED).setScale(0, RoundingMode.HALF_UP).longValueExact();
            case "PERCENTAGE" -> BigDecimal.valueOf(subtotalPaise).multiply(value)
                    .divide(ONE_HUNDRED, 0, RoundingMode.HALF_UP).longValueExact();
            default -> throw new IllegalArgumentException("Unsupported coupon type: " + type);
        };
        if (maximumPaise != null) calculated = Math.min(calculated, maximumPaise);
        return Math.min(calculated, subtotalPaise);
    }

    public static long gstAfterDiscount(List<TaxLine> lines, long couponDiscount) {
        List<Long> allocations = allocateDiscount(lines.stream().map(TaxLine::subtotalPaise).toList(), couponDiscount);
        long gst = 0;
        for (int index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            gst += CartCalculations.gst(line.subtotalPaise() - allocations.get(index), line.gstRatePercentage());
        }
        return gst;
    }

    public static List<Long> allocateDiscount(List<Long> subtotals, long couponDiscount) {
        long subtotal = subtotals.stream().mapToLong(Long::longValue).sum();
        if (subtotal <= 0) return subtotals.stream().map(ignored -> 0L).toList();
        long remainingDiscount = Math.min(Math.max(couponDiscount, 0), subtotal);
        long remainingSubtotal = subtotal;
        var allocations = new ArrayList<Long>(subtotals.size());
        for (int index = 0; index < subtotals.size(); index++) {
            long lineSubtotal = subtotals.get(index);
            long lineDiscount = index == subtotals.size() - 1 ? remainingDiscount
                    : BigDecimal.valueOf(remainingDiscount).multiply(BigDecimal.valueOf(lineSubtotal))
                    .divide(BigDecimal.valueOf(remainingSubtotal), 0, RoundingMode.DOWN).longValueExact();
            allocations.add(lineDiscount);
            remainingDiscount -= lineDiscount;
            remainingSubtotal -= lineSubtotal;
        }
        return List.copyOf(allocations);
    }

    public record TaxLine(long subtotalPaise, BigDecimal gstRatePercentage) {}
}
