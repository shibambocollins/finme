package com.finme.backend.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Baseline redaction pass (FR-1.3.1, FR-1.3.2): strips lines carrying account/ID/identity
 * labels and any stray long digit run (account numbers, phone numbers, branch codes) before
 * extracted statement text is built into an AI prompt. This is the regex/heuristic baseline
 * documented in docs/07-tech-stack.md - NER-based name redaction is an explicit future
 * improvement, not a v1 requirement. Enforced here, at the ingestion boundary, not left to
 * provider-side data-retention settings (docs/03-system-design.md Sec. 6).
 */
@Service
public class RedactionService {

    private static final String REDACTED = "[REDACTED]";

    // Lines that label an account/identity field are boilerplate, not transaction data -
    // drop the whole line rather than trying to preserve part of it.
    private static final Pattern LABELED_PII_LINE = Pattern.compile(
            "(?im)^.*\\b(account\\s*(no\\.?|number|holder(\\s*name)?)|acc\\s*no\\.?|" +
                    "id(entity)?\\s*number|client\\s*name|customer\\s*name|ssn|" +
                    "passport\\s*(no\\.?|number)|tax\\s*(ref(erence)?|number)|iban)\\s*[:\\-].*$"
    );

    // Catches stray account/phone/branch-code-like digit runs that appear without a label,
    // tolerating a single space or dash between digit groups (e.g. "123-456-7890",
    // "1234 5678 90") since real account/phone numbers are often formatted that way and a
    // strict \d{8,} misses them entirely. Dates (12/01/2026) and amounts (R450.00 or
    // R12,450.00) use slashes/periods/commas as separators, not spaces or dashes, so those
    // stay intact. Known limitation: a space-grouped amount of R10,000,000+ could still be
    // over-redacted - an accepted heuristic tradeoff, same as the duplicate-detection edge
    // case in docs/06-risk-register.md.
    private static final Pattern LONG_DIGIT_RUN = Pattern.compile("\\b\\d(?:[\\s-]?\\d){7,}\\b");

    public String redact(String rawText) {
        if (rawText == null) {
            return "";
        }
        String withoutLabeledLines = LABELED_PII_LINE.matcher(rawText).replaceAll(REDACTED);
        return LONG_DIGIT_RUN.matcher(withoutLabeledLines).replaceAll(REDACTED);
    }
}
