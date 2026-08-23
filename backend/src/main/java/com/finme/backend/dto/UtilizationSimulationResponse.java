package com.finme.backend.dto;

import java.math.BigDecimal;

/**
 * The result of a what-if (FR-2.3.2), as ratios - 0.42 is 42%.
 *
 * @param currentOverall  utilization as things stand
 * @param simulatedOverall utilization if this account held the hypothetical balance
 * @param change          simulated minus current; negative means the change would improve it
 * @param note            what this does and does not claim, so a falling number is not read as
 *                        a promised score movement
 */
public record UtilizationSimulationResponse(
        BigDecimal currentOverall,
        BigDecimal simulatedOverall,
        BigDecimal change,
        String note) {
}
