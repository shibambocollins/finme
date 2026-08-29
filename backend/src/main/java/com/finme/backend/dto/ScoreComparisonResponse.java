package com.finme.backend.dto;

import java.time.Instant;

public record ScoreComparisonResponse(
        Integer currentScore,
        Instant currentRecordedAt,
        Integer previousScore,
        Instant previousRecordedAt,
        Integer change,
        Long daysApart,
        String message) {
}
