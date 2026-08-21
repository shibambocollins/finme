package com.finme.backend.service;

import com.finme.backend.dto.DashboardSummaryResponse;
import com.finme.backend.entity.StatementStatus;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.repository.TransactionRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live end-to-end smoke test against the REAL provider chain - it spends real API quota, so it
 * only runs when explicitly asked for:
 *
 * <pre>./mvnw test -Dtest=LiveExtractionSmokeTest -Dlive.ai=true</pre>
 *
 * Everything else in the suite runs against mocks. This exists because mocked tests cannot
 * catch the class of failure that actually broke extraction in practice: a provider returning
 * a well-formed but truncated answer. Only a real call surfaces that.
 *
 * The fixture is a 16-transaction statement with known ground truth, deliberately including a
 * salary credit and a refund - the two rows that expose whether "total spend" is really spend.
 */
// src/test/resources/application.properties shadows the main one entirely and defines none of
// the provider keys, so ai.provider=chain alone cannot resolve them - each is pulled straight
// from the environment here (i.e. from backend/.env, via run-dev.sh's export or the IDE's
// envFile), with the same defaults the main properties file uses.
@SpringBootTest(properties = {
        "ai.provider=chain",
        "geocoding.provider=opencage",
        "opencage.api-key=${OPENCAGE_API_KEY:}",
        "opencage.country-code=${OPENCAGE_COUNTRY_CODE:za}",
        "groq.api-key=${GROQ_API_KEY:}",
        "groq.model=${GROQ_MODEL:openai/gpt-oss-20b}",
        "groq.vision-model=${GROQ_VISION_MODEL:qwen/qwen3.6-27b}",
        "openrouter.api-key=${OPENROUTER_API_KEY:}",
        "openrouter.model=${OPENROUTER_MODEL:nvidia/nemotron-3-super-120b-a12b:free}",
        "openrouter.vision-model=${OPENROUTER_VISION_MODEL:google/gemma-4-31b-it:free}",
        "cloudflare.account-id=${CLOUDFLARE_ACCOUNT_ID:}",
        "cloudflare.api-token=${CLOUDFLARE_API_TOKEN:}",
        "cloudflare.model=${CLOUDFLARE_MODEL:@cf/meta/llama-3.1-8b-instruct}",
        "cloudflare.vision-model=${CLOUDFLARE_VISION_MODEL:@cf/meta/llama-3.2-11b-vision-instruct}"
})
@EnabledIfSystemProperty(named = "live.ai", matches = "true")
class LiveExtractionSmokeTest {

    private static final long USER_ID = 990001L;

    private static final int EXPECTED_TRANSACTIONS = 16;

    /**
     * Ground truth for the fixture below: 14 debits totalling 8316.00, an 18500.00 salary
     * (money in, never spending - excluded entirely), and an 842.15 refund reversing the
     * 02 Jul Woolworths purchase (money in, but a reversal of real spending - subtracts).
     * Net spend is therefore 8316.00 - 842.15.
     */
    private static final BigDecimal GROSS_DEBITS = new BigDecimal("8316.00");
    private static final BigDecimal REFUND_CREDITS = new BigDecimal("842.15");
    private static final BigDecimal TRUE_SPEND = GROSS_DEBITS.subtract(REFUND_CREDITS);

    private static final List<String> STATEMENT_LINES = List.of(
            "STANDARD BANK OF SOUTH AFRICA - Cheque Account Statement",
            "Account Number: 1234 5678 9012",
            "Account Holder: C. Ntsobokwane",
            "Statement Period: 01 July 2026 to 31 July 2026",
            "Date     Description                        Debit     Credit",
            "01 Jul   SALARY ACB CREDIT MONTHLY                    18500.00",
            "02 Jul   WOOLWORTHS SANDTON CITY             842.15",
            "03 Jul   UBER TRIP CAPE TOWN                  87.50",
            "05 Jul   SHELL GARAGE RIVONIA                650.00",
            "07 Jul   NETFLIX SUBSCRIPTION                199.00",
            "09 Jul   KFC V&A WATERFRONT                  128.90",
            "11 Jul   CITY OF JHB ELECTRICITY PREPAID     900.00",
            "14 Jul   CLICKS PHARMACY ROSEBANK            243.75",
            "15 Jul   REFUND WOOLWORTHS SANDTON CITY                 842.15",
            "17 Jul   CHECKERS HYPER FOURWAYS            1256.40",
            "19 Jul   VODACOM PREPAID AIRTIME             250.00",
            "22 Jul   NANDOS MELROSE ARCH                 215.00",
            "25 Jul   GAUTRAIN CARD RECHARGE              300.00",
            "28 Jul   DISCOVERY HEALTH PREMIUM           2150.00",
            "30 Jul   PICK N PAY MENLYN PRETORIA          978.30",
            "31 Jul   BANK CHARGES MONTHLY FEE            115.00");

    @Autowired
    private StatementIngestionService statementIngestionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private DashboardService dashboardService;

    @Test
    void extractsEveryTransactionFromARealStatementViaTheRealChain() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.pdf", "application/pdf", buildStatementPdf());

        long startedAt = System.currentTimeMillis();
        var statement = statementIngestionService.ingest(USER_ID, file);
        long elapsedMs = System.currentTimeMillis() - startedAt;

        List<Transaction> saved = transactionRepository
                .findByUserIdAndStatusOrderByDateDesc(USER_ID, TransactionStatus.ACTIVE);
        DashboardSummaryResponse summary = dashboardService.getSummary(USER_ID);

        System.out.println("\n================ LIVE EXTRACTION RESULT ================");
        System.out.printf("status=%s  elapsed=%.1fs  extracted=%d (expected %d)%n",
                statement.getStatus(), elapsedMs / 1000.0, saved.size(), EXPECTED_TRANSACTIONS);
        System.out.printf("%-12s %-32s %10s  %-7s %-14s %s%n",
                "DATE", "MERCHANT", "AMOUNT", "DIR", "CATEGORY", "GEOCODED");
        System.out.println("-".repeat(100));
        saved.stream()
                .sorted((a, b) -> a.getDate().compareTo(b.getDate()))
                .forEach(t -> System.out.printf("%-12s %-32s %10s  %-7s %-14s %s%n",
                        t.getDate(),
                        truncate(t.getMerchant(), 31),
                        t.getAmount(),
                        t.getDirection(),
                        t.getCategory(),
                        t.getLatitude() == null ? "-" : t.getLatitude() + "," + t.getLongitude()));
        System.out.println("-".repeat(100));
        System.out.println("DASHBOARD total spend : " + summary.totalSpend());
        System.out.println("TRUE net spend        : " + TRUE_SPEND
                + "  (" + GROSS_DEBITS + " debits - " + REFUND_CREDITS + " refund)");
        System.out.println("DIFFERENCE            : " + summary.totalSpend().subtract(TRUE_SPEND));
        System.out.println("CATEGORY BREAKDOWN    :");
        summary.categoryBreakdown()
                .forEach(c -> System.out.printf("    %-16s %s%n", c.category(), c.amount()));
        System.out.println("MAP PINS              : " + summary.locations().size()
                + " placed, " + summary.locations().stream()
                .map(l -> l.latitude() + "," + l.longitude()).distinct().count() + " distinct");
        summary.locations().forEach(l -> System.out.printf("    %-32s %s,%s%s%n",
                truncate(l.merchant(), 31), l.latitude(), l.longitude(),
                l.approximate() ? "  (approximate)" : "  (exact)"));
        System.out.println("========================================================\n");

        assertThat(statement.getStatus()).isEqualTo(StatementStatus.COMPLETE);
        assertThat(saved).hasSize(EXPECTED_TRANSACTIONS);
        // compareTo, not isEqualTo - BigDecimal equality is scale-sensitive and 7473.85 vs
        // 7473.8500 would fail on a difference that does not exist.
        assertThat(summary.totalSpend()).usingComparator(BigDecimal::compareTo).isEqualTo(TRUE_SPEND);
        assertThat(summary.categoryBreakdown())
                .as("Income is money in, never a category of spend")
                .noneMatch(c -> "Income".equalsIgnoreCase(c.category()));
    }

    private static String truncate(String value, int max) {
        return value == null ? "" : value.length() <= max ? value : value.substring(0, max);
    }

    private static byte[] buildStatementPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER), 9);
                content.beginText();
                content.newLineAtOffset(40, 780);
                for (String line : STATEMENT_LINES) {
                    content.showText(line);
                    content.newLineAtOffset(0, -14);
                }
                content.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
