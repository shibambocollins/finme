package com.finme.backend.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits redacted statement text into pieces small enough for one AI call.
 * <p>
 * This is not an optimisation - a whole real statement cannot be extracted in a single call on
 * the free tiers this project targets, and trying produced silent data loss. Measured against
 * the live APIs on 2026-08-22:
 * <ul>
 *   <li>Groq's free tier caps <b>tokens per minute at 8000</b>, counting prompt tokens plus the
 *       requested max_tokens - so the output budget is spent from the rate limit whether the
 *       model uses it or not, and one oversized request is rejected outright with HTTP 413.</li>
 *   <li>OpenRouter accepted the same statement but hit its output limit part-way through and
 *       returned finish_reason "length" - a truncated transaction list.</li>
 * </ul>
 * Both failure modes come from asking for one enormous answer. Many small answers avoid both.
 * <p>
 * Every chunk repeats the statement header, because the header is where the year lives. Card
 * statements print "12 Jul" with no year, and a chunk of bare transaction rows gives the model
 * nothing to resolve that against - it would guess, and guess differently per chunk.
 */
@Component
public class StatementTextChunker {

    /**
     * Lines of transaction rows per chunk. Sized against the measured budget rather than
     * picked round: at roughly 130 output tokens per extracted transaction, 15 rows needs
     * ~2000 completion tokens, which with the repeated header keeps a single request near
     * 3200 tokens - comfortably inside the 8000 TPM ceiling.
     */
    static final int ROWS_PER_CHUNK = 15;

    /**
     * Upper bound on how many leading lines may be treated as header, for a statement whose
     * rows this class cannot recognise. Without a cap, an unrecognised layout would put the
     * entire document in the header and defeat chunking entirely.
     */
    static final int MAX_HEADER_LINES = 10;

    /**
     * Marks where transaction rows begin. The header must be found, not assumed at a fixed
     * offset: a fixed count silently swept real transaction rows into the header, and because
     * the header is repeated into every chunk those rows were then extracted once per chunk.
     * An 80-row statement came back as 95 transactions - three rows duplicated across six
     * chunks - which is worse than losing them, since inflated spend still looks plausible.
     * <p>
     * Covers the common statement date styles: "01 Jul", "12/01/2026", "2026-07-01".
     */
    private static final Pattern TRANSACTION_ROW = Pattern.compile(
            "^\\s*(\\d{1,2}\\s+[A-Za-z]{3}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}-\\d{2}-\\d{2})\\b");

    public List<String> chunk(String redactedText) {
        if (redactedText == null || redactedText.isBlank()) {
            return List.of();
        }

        List<String> lines = redactedText.lines().map(String::stripTrailing).toList();
        int headerEnd = findFirstTransactionRow(lines);

        if (lines.size() - headerEnd <= ROWS_PER_CHUNK) {
            return List.of(redactedText);
        }

        String header = String.join("\n", lines.subList(0, headerEnd));
        List<String> body = lines.subList(headerEnd, lines.size());

        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < body.size(); start += ROWS_PER_CHUNK) {
            List<String> window = body.subList(start, Math.min(start + ROWS_PER_CHUNK, body.size()));
            if (window.stream().allMatch(String::isBlank)) {
                continue;
            }
            chunks.add(header.isEmpty() ? String.join("\n", window) : header + "\n" + String.join("\n", window));
        }
        return chunks;
    }

    /**
     * Index of the first line that looks like a transaction row, i.e. where the header ends.
     * Falls back to the cap when no row is recognised, so an unfamiliar layout still gets
     * chunked rather than being sent whole.
     */
    private int findFirstTransactionRow(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (TRANSACTION_ROW.matcher(lines.get(i)).find()) {
                return i;
            }
        }
        return Math.min(MAX_HEADER_LINES, lines.size());
    }
}
