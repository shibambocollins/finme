package com.finme.backend.evaluation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

record GoldenDocument(String name, byte[] bytes, String mimeType, List<GoldenTransaction> expected) {

    /**
     * A labeled transaction. Deliberately narrower than the app's own Transaction: the golden
     * set records only what a human can verify by reading the document, so there is nothing in
     * here that a labeler would have to guess at.
     */
    record GoldenTransaction(LocalDate date, String merchant, BigDecimal amount, String category) {
    }
}
