package com.finme.backend.evaluation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One labeled source document in the golden set (FR-1.9.1, FR-1.9.2): the bytes that go into a
 * pipeline, plus the transactions a human says are actually in it.
 *
 * @param name         identifies the document in the report
 * @param bytes        the PDF or image exactly as it would be uploaded
 * @param mimeType     drives which pipeline the harness routes it through
 * @param expected     the ground truth - what a correct extraction would return
 */
record GoldenDocument(String name, byte[] bytes, String mimeType, List<GoldenTransaction> expected) {

    /**
     * A labeled transaction. Deliberately narrower than the app's own Transaction: the golden
     * set records only what a human can verify by reading the document, so there is nothing in
     * here that a labeler would have to guess at.
     */
    record GoldenTransaction(LocalDate date, String merchant, BigDecimal amount, String category) {
    }
}
