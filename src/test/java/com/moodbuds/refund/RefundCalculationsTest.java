package com.moodbuds.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class RefundCalculationsTest {
    @Test
    void floorsPartialItemAllocationInPaise() {
        assertThat(RefundCalculations.itemAmount(1_000, 3, 0, 0, 1)).isEqualTo(333);
    }

    @Test
    void assignsRoundingRemainderToFinalReturnedQuantity() {
        assertThat(RefundCalculations.itemAmount(1_000, 3, 1, 333, 2)).isEqualTo(667);
    }

    @Test
    void rejectsQuantityBeyondPurchasedQuantity() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RefundCalculations.itemAmount(1_000, 2, 1, 500, 2));
    }
}
