package com.finme.backend.redaction;

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
                    "id(entity)?\\s*number|client\\s*name|customer\\s*name)\\s*[:\\-].*$"
    );

    // Catches stray account/phone/branch-code-like digit runs that appear without a label.
    // Transaction amounts and dates in statement text don't reach 8 consecutive digits, so
    // this does not clip legitimate transaction data.
    private static final Pattern LONG_DIGIT_RUN = Pattern.compile("\\b\\d{8,}\\b");

    public String redact(String rawText) {
        if (rawText == null) {
            return "";
        }
        String withoutLabeledLines = LABELED_PII_LINE.matcher(rawText).replaceAll(REDACTED);
        return LONG_DIGIT_RUN.matcher(withoutLabeledLines).replaceAll(REDACTED);
    }
}
