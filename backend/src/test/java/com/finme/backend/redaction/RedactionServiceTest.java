package com.finme.backend.redaction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedactionServiceTest {

    private final RedactionService redactionService = new RedactionService();

    @Test
    void stripsLabeledAccountNumberLine() {
        String result = redactionService.redact("Account Number: 1234567890\nDate Merchant Amount");

        assertThat(result).doesNotContain("1234567890");
        assertThat(result).contains("[REDACTED]");
    }

    @Test
    void stripsLabeledIdNumberLine() {
        String result = redactionService.redact("ID Number: 9001015800086");

        assertThat(result).doesNotContain("9001015800086");
    }

    @Test
    void stripsStrayLongDigitRunsEvenWithoutALabel() {
        String result = redactionService.redact("Some stray reference 98765432109 in the footer");

        assertThat(result).doesNotContain("98765432109");
    }

    @Test
    void leavesTransactionDatesAndAmountsIntact() {
        String line = "12/01/2026   Woolworths Sandton   R450.00";

        assertThat(redactionService.redact(line)).isEqualTo(line);
    }

    @Test
    void doesNotRedactMerchantDataJustBecauseItSaysName() {
        String result = redactionService.redact("Merchant Name: Woolworths\n12/01/2026 R450.00");

        assertThat(result).contains("Woolworths");
    }

    @Test
    void handlesNullInput() {
        assertThat(redactionService.redact(null)).isEmpty();
    }
}
