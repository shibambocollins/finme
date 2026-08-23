package com.finme.backend.evaluation;

import com.finme.backend.ai.ExtractedTransaction;
import com.finme.backend.evaluation.GoldenDocument.GoldenTransaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The harness measures the pipeline; these measure the harness. Without them the project would
 * be reporting accuracy figures whose own correctness was assumed - the exact habit the
 * evaluation harness exists to break.
 */
class ExtractionEvaluatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 2);

    private static GoldenTransaction expected(String amount, String category) {
        return new GoldenTransaction(DAY, "Woolworths", new BigDecimal(amount), category);
    }

    private static ExtractedTransaction actual(String amount, String category) {
        return new ExtractedTransaction(DAY, "Woolworths", new BigDecimal(amount), category, "desc");
    }

    private static ExtractionMetrics metricsOf(ExtractionEvaluator.Result r) {
        return new ExtractionMetrics("test", 1, r.truePositives, r.falsePositives, r.falseNegatives,
                r.categoryHits, r.missed, r.invented, r.miscategorised, List.of());
    }

    @Test
    void scoresAPerfectExtraction() {
        var result = ExtractionEvaluator.score("doc",
                List.of(expected("100.00", "Groceries"), expected("50.00", "Transport")),
                List.of(actual("100.00", "Groceries"), actual("50.00", "Transport")));

        ExtractionMetrics metrics = metricsOf(result);
        assertThat(metrics.precision()).isEqualTo(1.0);
        assertThat(metrics.recall()).isEqualTo(1.0);
        assertThat(metrics.f1()).isEqualTo(1.0);
        assertThat(metrics.categoryAccuracy()).isEqualTo(1.0);
        assertThat(metrics.hallucinationRate()).isEqualTo(0.0);
    }

    @Test
    void countsAMissedTransactionAgainstRecallButNotPrecision() {
        var result = ExtractionEvaluator.score("doc",
                List.of(expected("100.00", "Groceries"), expected("50.00", "Transport")),
                List.of(actual("100.00", "Groceries")));

        ExtractionMetrics metrics = metricsOf(result);
        assertThat(metrics.recall()).isEqualTo(0.5);
        assertThat(metrics.precision()).isEqualTo(1.0);
        assertThat(metrics.missed()).singleElement().asString().contains("50.00");
    }

    @Test
    void countsAnInventedTransactionAgainstPrecisionAndAsHallucination() {
        var result = ExtractionEvaluator.score("doc",
                List.of(expected("100.00", "Groceries")),
                List.of(actual("100.00", "Groceries"), actual("999.99", "Dining")));

        ExtractionMetrics metrics = metricsOf(result);
        assertThat(metrics.precision()).isEqualTo(0.5);
        assertThat(metrics.recall()).isEqualTo(1.0);
        assertThat(metrics.hallucinationRate()).isEqualTo(0.5);
        assertThat(metrics.invented()).singleElement().asString().contains("999.99");
    }

    @Test
    void doesNotRewardTheSameTransactionReportedTwice() {
        // The duplicated-header chunking bug produced exactly this shape - an 80-row statement
        // reported as 95 transactions. Without one-to-one pairing it would have scored as
        // perfect recall and been invisible here.
        var result = ExtractionEvaluator.score("doc",
                List.of(expected("100.00", "Groceries")),
                List.of(actual("100.00", "Groceries"), actual("100.00", "Groceries")));

        ExtractionMetrics metrics = metricsOf(result);
        assertThat(metrics.truePositives()).isEqualTo(1);
        assertThat(metrics.falsePositives()).isEqualTo(1);
        assertThat(metrics.precision()).isEqualTo(0.5);
    }

    @Test
    void treatsDifferingScaleAsTheSameAmount() {
        // 450.0 and 450.00 are the same money; BigDecimal.equals disagrees, which would have
        // scored a correct extraction as both a miss and an invention.
        var result = ExtractionEvaluator.score("doc",
                List.of(expected("450.0", "Groceries")),
                List.of(actual("450.00", "Groceries")));

        assertThat(metricsOf(result).recall()).isEqualTo(1.0);
    }

    @Test
    void countsAWrongCategoryAsFoundButMiscategorised() {
        var result = ExtractionEvaluator.score("doc",
                List.of(expected("100.00", "Groceries")),
                List.of(actual("100.00", "Dining")));

        ExtractionMetrics metrics = metricsOf(result);
        assertThat(metrics.recall()).isEqualTo(1.0);
        assertThat(metrics.categoryAccuracy()).isEqualTo(0.0);
        assertThat(metrics.miscategorised()).singleElement().asString()
                .contains("expected Groceries, got Dining");
    }

    @Test
    void doesNotPenaliseCategoryAccuracyForTransactionsItNeverFound() {
        // Detection failures belong to recall. Blending them into category accuracy would
        // report a pipeline that categorises perfectly as though it categorises badly.
        var result = ExtractionEvaluator.score("doc",
                List.of(expected("100.00", "Groceries"), expected("50.00", "Transport")),
                List.of(actual("100.00", "Groceries")));

        ExtractionMetrics metrics = metricsOf(result);
        assertThat(metrics.categoryAccuracy()).isEqualTo(1.0);
        assertThat(metrics.recall()).isEqualTo(0.5);
    }

    @Test
    void treatsAWrongDateAsBothAMissAndAnInvention() {
        // Strict on purpose: a transaction dated to the wrong month lands in the wrong period
        // on the dashboard, so it is not a partial success.
        var result = ExtractionEvaluator.score("doc",
                List.of(expected("100.00", "Groceries")),
                List.of(new ExtractedTransaction(DAY.plusYears(1), "Woolworths",
                        new BigDecimal("100.00"), "Groceries", "desc")));

        ExtractionMetrics metrics = metricsOf(result);
        assertThat(metrics.truePositives()).isZero();
        assertThat(metrics.falseNegatives()).isEqualTo(1);
        assertThat(metrics.falsePositives()).isEqualTo(1);
    }

    @Test
    void ignoresMerchantWordingDifferences() {
        // Models paraphrase merchant text constantly; scoring detection on it would invent an
        // accuracy problem out of a naming difference.
        var result = ExtractionEvaluator.score("doc",
                List.of(new GoldenTransaction(DAY, "WOOLWORTHS SANDTON CITY", new BigDecimal("100.00"), "Groceries")),
                List.of(new ExtractedTransaction(DAY, "Woolworths", new BigDecimal("100.00"), "Groceries", "d")));

        assertThat(metricsOf(result).recall()).isEqualTo(1.0);
    }

    @Test
    void computesF1AsTheHarmonicMeanOfPrecisionAndRecall() {
        var result = ExtractionEvaluator.score("doc",
                List.of(expected("100.00", "Groceries"), expected("50.00", "Transport")),
                List.of(actual("100.00", "Groceries"), actual("999.99", "Dining")));

        ExtractionMetrics metrics = metricsOf(result);
        assertThat(metrics.precision()).isEqualTo(0.5);
        assertThat(metrics.recall()).isEqualTo(0.5);
        assertThat(metrics.f1()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void reportsZeroesRatherThanDividingByZeroOnAnEmptyRun() {
        var result = ExtractionEvaluator.score("doc", List.of(), List.of());

        ExtractionMetrics metrics = metricsOf(result);
        assertThat(metrics.precision()).isEqualTo(0.0);
        assertThat(metrics.recall()).isEqualTo(0.0);
        assertThat(metrics.f1()).isEqualTo(0.0);
        assertThat(metrics.hallucinationRate()).isEqualTo(0.0);
    }
}
