package com.moodbuds.quiz;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuizSessionTokenServiceTest {
    private final QuizSessionTokenService service = new QuizSessionTokenService();

    @Test
    void createsDistinctTokensAndStoresOnlyHashes() {
        var first = service.create();
        var second = service.create();

        assertThat(first.raw()).isNotEqualTo(first.hash());
        assertThat(first.raw()).isNotEqualTo(second.raw());
        assertThat(first.hash()).hasSize(64);
        assertThat(service.matches(first.raw(), first.hash())).isTrue();
        assertThat(service.matches(second.raw(), first.hash())).isFalse();
    }
}
