package com.moodbuds.quiz;

import java.util.Comparator;
import java.util.List;

import com.moodbuds.quiz.QuizScoringPolicy.ScoreCandidate;
import org.springframework.stereotype.Component;

@Component
public class QuizScoringPolicy {
    public List<ScoreCandidate> rank(List<ScoreCandidate> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparingInt(ScoreCandidate::totalScore).reversed()
                        .thenComparing(Comparator.comparingInt(ScoreCandidate::finalAnswerScore).reversed())
                        .thenComparingLong(ScoreCandidate::moodId))
                .toList();
    }

    public record ScoreCandidate(
            long moodId,
            String name,
            String slug,
            String tagline,
            String personalityTagline,
            String bannerImageUrl,
            String color,
            int totalScore,
            int finalAnswerScore) {}
}
