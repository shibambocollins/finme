package com.finme.backend.evaluation;

import com.finme.backend.ai.ExtractedTransaction;
import com.finme.backend.evaluation.GoldenDocument.GoldenTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Scores extracted transactions against a labeled golden set (FR-1.9.3).
 * <p>
 * Pure arithmetic over two lists - it performs no extraction and makes no network calls, so the
 * metric logic can be unit-tested exactly. That separation is the point: a harness whose own
 * scoring is unverified would report confident numbers about accuracy while being wrong itself,
 * which is precisely the failure it exists to catch.
 */
final class ExtractionEvaluator {

    private ExtractionEvaluator() {
    }

    /**
     * A prediction matches a label when the date and amount agree exactly.
     * <p>
     * Merchant text is deliberately excluded from the match. Models paraphrase it constantly -
     * "WOOLWORTHS SANDTON CITY" comes back as "Woolworths", "Woolworths Sandton", or
     * "WOOLWORTHS SANDTON CITY" depending on the model and the run - and scoring detection on
     * string equality would report those as both a miss and an invention, turning a naming
     * difference into a fabricated accuracy problem. Date plus amount identifies a transaction
     * on a statement; the merchant is what the app then displays.
     * <p>
     * Amounts are compared with compareTo, not equals: 450.0 and 450.00 are the same money and
     * differ only in scale, which BigDecimal.equals treats as unequal.
     */
    private static boolean matches(GoldenTransaction expected, ExtractedTransaction actual) {
        return actual.date() != null
                && actual.amount() != null
                && expected.date().equals(actual.date())
                && expected.amount().compareTo(actual.amount()) == 0;
    }

    /**
     * Greedy one-to-one pairing. Each labeled transaction consumes at most one prediction, so a
     * pipeline that emits the same transaction twice scores one match and one invention rather
     * than two matches - duplicates inflate a total and must not be rewarded.
     */
    static Result score(String documentName, List<GoldenTransaction> expected, List<ExtractedTransaction> actual) {
        boolean[] consumed = new boolean[actual.size()];
        Result result = new Result();

        for (GoldenTransaction label : expected) {
            int matchIndex = -1;
            for (int i = 0; i < actual.size(); i++) {
                if (!consumed[i] && matches(label, actual.get(i))) {
                    matchIndex = i;
                    break;
                }
            }

            if (matchIndex < 0) {
                result.falseNegatives++;
                result.missed.add(describe(documentName, label.date(), label.merchant(), label.amount()));
                continue;
            }

            consumed[matchIndex] = true;
            result.truePositives++;

            ExtractedTransaction matched = actual.get(matchIndex);
            if (label.category() != null && label.category().equalsIgnoreCase(matched.category())) {
                result.categoryHits++;
            } else {
                result.miscategorised.add(String.format("%s: %s %s - expected %s, got %s",
                        documentName, label.date(), label.merchant(), label.category(), matched.category()));
            }
        }

        for (int i = 0; i < actual.size(); i++) {
            if (!consumed[i]) {
                ExtractedTransaction invented = actual.get(i);
                result.falsePositives++;
                result.invented.add(describe(documentName, invented.date(), invented.merchant(), invented.amount()));
            }
        }
        return result;
    }

    private static String describe(String document, LocalDate date, String merchant, BigDecimal amount) {
        return String.format("%s: %s %s %s", document, date, merchant, amount);
    }

    /** Per-document counts, summed by the harness into one {@link ExtractionMetrics}. */
    static final class Result {
        int truePositives;
        int falsePositives;
        int falseNegatives;
        int categoryHits;
        final List<String> missed = new ArrayList<>();
        final List<String> invented = new ArrayList<>();
        final List<String> miscategorised = new ArrayList<>();
    }
}
