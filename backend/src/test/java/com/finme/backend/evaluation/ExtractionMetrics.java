package com.finme.backend.evaluation;

import java.util.List;
import java.util.Locale;

/**
 * Scores for one pipeline over one golden set (FR-1.9.3).
 * <p>
 * Every rate is derived from the three counts, never stored independently, so the report cannot
 * drift from the evidence behind it. The unmatched lists are kept because an accuracy figure on
 * its own is not actionable - when recall drops, the first question is always "which ones did it
 * miss", and a harness that cannot answer that just tells you to go and look manually.
 *
 * @param truePositives  extracted transactions that matched a labeled one
 * @param falsePositives extracted transactions matching nothing in the document's labels
 * @param falseNegatives labeled transactions the pipeline did not find
 * @param categoryHits   among matched transactions, how many also had the right category
 */
record ExtractionMetrics(
        String pipeline,
        int documents,
        int truePositives,
        int falsePositives,
        int falseNegatives,
        int categoryHits,
        List<String> missed,
        List<String> invented,
        List<String> miscategorised,
        List<String> failures
) {

    /** Of everything the pipeline reported, how much was real. */
    double precision() {
        int reported = truePositives + falsePositives;
        return reported == 0 ? 0.0 : (double) truePositives / reported;
    }

    /** Of everything actually in the documents, how much the pipeline found. */
    double recall() {
        int actual = truePositives + falseNegatives;
        return actual == 0 ? 0.0 : (double) truePositives / actual;
    }

    double f1() {
        double p = precision();
        double r = recall();
        return (p + r) == 0 ? 0.0 : 2 * p * r / (p + r);
    }

    /**
     * Scored over matched transactions only, and that choice matters. Including unmatched ones
     * would blend two different failures into one number - a pipeline that misses half a
     * statement would post a poor "category accuracy" even if it categorised everything it did
     * find perfectly. Detection is measured by recall; this measures categorisation alone.
     */
    double categoryAccuracy() {
        return truePositives == 0 ? 0.0 : (double) categoryHits / truePositives;
    }

    /**
     * The share of reported transactions that are not in the source document.
     * <p>
     * This is 1 - precision by construction, and is reported anyway because it is the number
     * that actually answers the question a user of a finance app asks: "how often does it make
     * things up?" Precision phrases the same evidence as a virtue; for money-tracking software
     * the failure is the more useful framing, and FR-1.9.3 asks for it explicitly.
     */
    double hallucinationRate() {
        int reported = truePositives + falsePositives;
        return reported == 0 ? 0.0 : (double) falsePositives / reported;
    }

    /**
     * Locale.ROOT throughout, not the default locale. On this machine the default renders 1.000
     * as "1,000" - which reads as one thousand, and makes reports impossible to diff or parse
     * across runs. A measurement's format is part of the measurement.
     */
    String render() {
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "%n=== %s pipeline ===%n", pipeline));
        out.append(String.format(Locale.ROOT, "documents            : %d%n", documents));
        out.append(String.format(Locale.ROOT, "expected transactions: %d%n", truePositives + falseNegatives));
        out.append(String.format(Locale.ROOT, "reported transactions: %d%n", truePositives + falsePositives));
        out.append(String.format(Locale.ROOT, "  matched            : %d%n", truePositives));
        out.append(String.format(Locale.ROOT, "  missed             : %d%n", falseNegatives));
        out.append(String.format(Locale.ROOT, "  invented           : %d%n", falsePositives));
        out.append(String.format(Locale.ROOT, "precision            : %.3f%n", precision()));
        out.append(String.format(Locale.ROOT, "recall               : %.3f%n", recall()));
        out.append(String.format(Locale.ROOT, "F1                   : %.3f%n", f1()));
        out.append(String.format(Locale.ROOT, "category accuracy    : %.3f%n", categoryAccuracy()));
        out.append(String.format(Locale.ROOT, "hallucination rate   : %.3f%n", hallucinationRate()));
        appendList(out, "MISSED (in document, not reported)", missed);
        appendList(out, "INVENTED (reported, not in document)", invented);
        appendList(out, "MISCATEGORISED (found, wrong category)", miscategorised);
        appendList(out, "DOCUMENT FAILURES", failures);
        return out.toString();
    }

    private static void appendList(StringBuilder out, String heading, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        out.append(heading).append(":\n");
        items.forEach(item -> out.append("  - ").append(item).append('\n'));
    }
}
