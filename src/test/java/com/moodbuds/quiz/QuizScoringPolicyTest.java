package com.moodbuds.quiz;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuizScoringPolicyTest {
    private final QuizScoringPolicy policy = new QuizScoringPolicy();

    @Test
    void ranksByTotalThenFinalAnswerThenStableMoodId() {
        var lowerTotal = candidate(9, 10, 5);
        var sameTotalLowerFinal = candidate(7, 12, 1);
        var sameTotalHigherFinalHigherId = candidate(5, 12, 3);
        var sameTotalHigherFinalLowerId = candidate(2, 12, 3);

        var ranked = policy.rank(List.of(lowerTotal, sameTotalLowerFinal,
                sameTotalHigherFinalHigherId, sameTotalHigherFinalLowerId));

        assertThat(ranked).extracting(QuizScoringPolicy.ScoreCandidate::moodId)
                .containsExactly(2L, 5L, 7L, 9L);
    }

    private QuizScoringPolicy.ScoreCandidate candidate(long id, int total, int finalScore) {
        return new QuizScoringPolicy.ScoreCandidate(id, "Mood " + id, "mood-" + id,
                null, null, null, null, total, finalScore);
    }
}
