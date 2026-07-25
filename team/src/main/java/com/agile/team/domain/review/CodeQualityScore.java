package com.agile.team.domain.review;

public record CodeQualityScore(double score, boolean passed) {
    public CodeQualityScore {
        if (score < 0 || score > 100) throw new IllegalArgumentException("Score must be between 0 and 100");
    }

    public static CodeQualityScore passing(double score) {
        return new CodeQualityScore(score, true);
    }

    public static CodeQualityScore failing(double score) {
        return new CodeQualityScore(score, false);
    }
}
