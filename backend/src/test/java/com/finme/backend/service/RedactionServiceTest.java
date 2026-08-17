package com.finme.backend.service;

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

    @Test
    void stripsSpaceSeparatedDigitRuns() {
        String result = redactionService.redact("Account ref 1234 5678 90 on file");

        assertThat(result).doesNotContain("1234 5678 90");
    }

    @Test
    void stripsDashSeparatedDigitRuns() {
        String result = redactionService.redact("Contact 012-345-6789 for queries");

        assertThat(result).doesNotContain("012-345-6789");
    }

    @Test
    void stripsLabeledSsnLine() {
        String result = redactionService.redact("SSN: 123-45-6789");

        assertThat(result).doesNotContain("123-45-6789");
    }

    @Test
    void stripsLabeledPassportNumberLine() {
        String result = redactionService.redact("Passport Number: A1234567");

        assertThat(result).doesNotContain("A1234567");
    }

    @Test
    void stripsLabeledTaxReferenceLine() {
        String result = redactionService.redact("Tax Reference: 1234567890");

        assertThat(result).doesNotContain("1234567890");
    }

    @Test
    void stripsLabeledIbanLine() {
        String result = redactionService.redact("IBAN: GB29NWBK60161331926819");

        assertThat(result).doesNotContain("GB29NWBK60161331926819");
    }

    @Test
    void leavesTransactionAmountsIntactEvenWithWidenedDigitRunPattern() {
        String line = "14/01/2026   Uber   R85.50\n12/01/2026   Woolworths Sandton   R12,450.00";

        assertThat(redactionService.redact(line)).isEqualTo(line);
    }
}
