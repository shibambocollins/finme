package com.finme.backend.dto;

import java.time.Instant;

/**
 * Current score against an earlier one (FR-2.4.2).
 *
 * @param change   current minus previous - positive is an improvement
 * @param daysApart how far apart the two readings are, because a 20-point move over a week and
 *                  over two years are different stories and the numbers alone do not say which
 */
public record ScoreComparisonResponse(
        Integer currentScore,
        Instant currentRecordedAt,
        Integer previousScore,
        Instant previousRecordedAt,
        Integer change,
        Long daysApart,
        String message) {
}
