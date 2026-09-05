package com.moodbuds.customer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CustomerTokenServiceTest {
    private final CustomerTokenService tokens = new CustomerTokenService();

    @Test
    void createsOpaqueRefreshTokensAndConstantTimeVerifiableHashes() {
        var first = tokens.create();
        var second = tokens.create();
        assertThat(first.raw()).isNotEqualTo(first.hash());
        assertThat(first.hash()).hasSize(64);
        assertThat(first.raw()).isNotEqualTo(second.raw());
        assertThat(tokens.matches(first.raw(), first.hash())).isTrue();
        assertThat(tokens.matches(second.raw(), first.hash())).isFalse();
    }
}
