package com.finme.backend.service;

import com.finme.backend.dto.DashboardSummaryResponse;
import com.finme.backend.entity.StatementStatus;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.entity.BankStatement;
import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.TransactionDirection;
import com.finme.backend.repository.BankStatementRepository;
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
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private BankStatementRepository bankStatementRepository;

    @Autowired
    private SpendAnalysisService spendAnalysisService;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private DashboardService dashboardService;

    @Test
    void extractsEveryTransactionFromARealStatementViaTheRealChain() throws IOException, InterruptedException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.pdf", "application/pdf", buildStatementPdf());

        long startedAt = System.currentTimeMillis();
        var statement = awaitSettled(statementIngestionService.ingest(USER_ID, file).getId());
        long elapsedMs = System.currentTimeMillis() - startedAt;

        List<Transaction> saved = transactionRepository
                .findByUserIdAndStatusOrderByDateDesc(USER_ID, TransactionStatus.ACTIVE);
        DashboardSummaryResponse summary = dashboardService.getSummary(USER_ID);

        System.out.println("\n================ LIVE EXTRACTION RESULT ================");
        System.out.printf("status=%s  elapsed=%.1fs  extracted=%d (expected %d)%n",
                statement.getStatus(), elapsedMs / 1000.0, saved.size(), EXPECTED_TRANSACTIONS);
        System.out.printf("%-12s %-32s %10s  %-7s %s%n",
                "DATE", "MERCHANT", "AMOUNT", "DIR", "CATEGORY");
        System.out.println("-".repeat(100));
        saved.stream()
                .sorted((a, b) -> a.getDate().compareTo(b.getDate()))
                .forEach(t -> System.out.printf("%-12s %-32s %10s  %-7s %s%n",
                        t.getDate(),
                        truncate(t.getMerchant(), 31),
                        t.getAmount(),
                        t.getDirection(),
                        t.getCategory()));
        System.out.println("-".repeat(100));
        System.out.println("DASHBOARD total spend : " + summary.totalSpend());
        System.out.println("TRUE net spend        : " + TRUE_SPEND
                + "  (" + GROSS_DEBITS + " debits - " + REFUND_CREDITS + " refund)");
        System.out.println("DIFFERENCE            : " + summary.totalSpend().subtract(TRUE_SPEND));
        System.out.println("CATEGORY BREAKDOWN    :");
        summary.categoryBreakdown()
                .forEach(c -> System.out.printf("    %-16s %s%n", c.category(), c.amount()));
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

    /**
     * Waits out the background extraction. The timeout is generous because that is precisely
     * what this test exercises: a large statement is deliberately paced by provider rate limits
     * and legitimately takes minutes.
     */
    private BankStatement awaitSettled(Long statementId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 600_000;
        while (System.currentTimeMillis() < deadline) {
            BankStatement statement = bankStatementRepository.findById(statementId).orElseThrow();
            if (statement.getStatus() != StatementStatus.PROCESSING) {
                return statement;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Statement " + statementId + " was still PROCESSING after 10 minutes");
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

    /**
     * The size that actually broke in production use. A 16-row statement fits in one call and
     * proves nothing about chunking, rate limits, or retry - this one needs six calls and more
     * tokens than the free tier allows in a minute, so it exercises the paths that failed.
     */
    @Test
    void handlesAStatementTooLargeForASingleCall() throws IOException, InterruptedException {
        long userId = 990002L;
        int rows = 80;
        MockMultipartFile file = new MockMultipartFile(
                "file", "large-statement.pdf", "application/pdf", buildLargeStatementPdf(rows));

        long startedAt = System.currentTimeMillis();
        var statement = awaitSettled(statementIngestionService.ingest(userId, file).getId());
        double elapsed = (System.currentTimeMillis() - startedAt) / 1000.0;

        List<Transaction> saved = transactionRepository
                .findByUserIdAndStatusOrderByDateDesc(userId, TransactionStatus.ACTIVE);

        System.out.println("\n============ LARGE STATEMENT ============");
        System.out.printf("rows in statement : %d%n", rows);
        System.out.printf("extracted         : %d%n", saved.size());
        System.out.printf("elapsed           : %.1fs%n", elapsed);
        System.out.printf("status            : %s%n", statement.getStatus());
        System.out.println("========================================\n");

        assertThat(statement.getStatus()).isEqualTo(StatementStatus.COMPLETE);
        // Exact-count assertions are wrong at this size: the fixture repeats merchants, so a
        // model may legitimately merge or split a row. What must hold is that chunking did not
        // quietly lose most of the statement - the old behaviour returned 18 of 80.
        assertThat(saved.size())
                .as("chunked extraction should recover nearly all rows, not a truncated head")
                .isGreaterThanOrEqualTo((int) (rows * 0.9));
    }

    private static byte[] buildLargeStatementPdf(int rows) throws IOException {
        String[] merchants = {
                "WOOLWORTHS SANDTON CITY", "UBER TRIP CAPE TOWN", "SHELL GARAGE RIVONIA",
                "NETFLIX SUBSCRIPTION", "KFC V&A WATERFRONT", "CITY OF JHB ELECTRICITY",
                "CLICKS PHARMACY ROSEBANK", "CHECKERS HYPER FOURWAYS", "VODACOM PREPAID AIRTIME",
                "NANDOS MELROSE ARCH", "GAUTRAIN CARD RECHARGE", "DISCOVERY HEALTH PREMIUM",
                "PICK N PAY MENLYN", "BANK CHARGES MONTHLY FEE", "TAKEALOT ONLINE ORDER",
                "SPAR PARKTOWN NORTH"};

        List<String> lines = new java.util.ArrayList<>(List.of(
                "STANDARD BANK - Cheque Account Statement",
                "Statement Period: 01 July 2026 to 31 July 2026",
                "Date     Description                        Debit     Credit"));
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < rows; i++) {
            double amount = 35 + random.nextDouble() * 2365;
            lines.add(String.format("%02d Jul   %-34s %8.2f", (i % 28) + 1, merchants[i % merchants.length], amount));
        }

        try (PDDocument document = new PDDocument()) {
            PDPageContentStream content = null;
            int lineOnPage = 0;
            PDPage page = null;
            try {
                for (String line : lines) {
                    if (content == null || lineOnPage >= 60) {
                        if (content != null) {
                            content.endText();
                            content.close();
                        }
                        page = new PDPage();
                        document.addPage(page);
                        content = new PDPageContentStream(document, page);
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER), 9);
                        content.beginText();
                        content.newLineAtOffset(40, 780);
                        lineOnPage = 0;
                    }
                    content.showText(line);
                    content.newLineAtOffset(0, -12);
                    lineOnPage++;
                }
            } finally {
                if (content != null) {
                    content.endText();
                    content.close();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    /**
     * The one thing that can quietly break FR-2.2.1: a model that helpfully does its own
     * arithmetic. Every figure in a recommendation must appear verbatim in the facts the
     * application calculated and supplied - anything else is a number the app cannot stand
     * behind, however plausible it reads.
     */
    @Test
    void recommendationsQuoteOnlyFiguresTheApplicationCalculated() {
        long userId = 990003L;
        seedSpendingFor(userId);

        String facts = spendAnalysisService.factsFor(userId).asPromptText();
        var response = recommendationService.getRecommendations(userId);

        System.out.println("\n============ LIVE RECOMMENDATIONS ============");
        System.out.println(facts);
        response.recommendations().forEach(r -> System.out.println("  - " + r));
        System.out.println("=============================================\n");

        assertThat(response.unavailableReason()).isNull();
        assertThat(response.recommendations()).isNotEmpty();

        Set<String> suppliedFigures = new HashSet<>();
        Matcher supplied = FIGURE.matcher(facts);
        while (supplied.find()) {
            suppliedFigures.add(supplied.group());
        }

        for (String recommendation : response.recommendations()) {
            Matcher quoted = FIGURE.matcher(recommendation);
            while (quoted.find()) {
                assertThat(suppliedFigures)
                        .as("figure %s in %s was not among the supplied facts", quoted.group(), recommendation)
                        .contains(quoted.group());
            }
        }
    }

    private static final Pattern FIGURE = Pattern.compile("\\d[\\d.]*");

    /** A small two-month spread, so there is a real month-over-month change to narrate. */
    private void seedSpendingFor(long userId) {
        record Row(LocalDate date, String merchant, String amount, String category) {
        }
        List<Row> rows = List.of(
                new Row(LocalDate.of(2026, 6, 4), "Checkers", "1580.10", "Groceries"),
                new Row(LocalDate.of(2026, 6, 9), "Discovery", "2150.00", "Health"),
                new Row(LocalDate.of(2026, 6, 18), "Uber", "890.30", "Transport"),
                new Row(LocalDate.of(2026, 7, 3), "Woolworths", "2234.70", "Groceries"),
                new Row(LocalDate.of(2026, 7, 11), "Discovery", "2393.75", "Health"),
                new Row(LocalDate.of(2026, 7, 19), "Uber", "1037.50", "Transport"),
                new Row(LocalDate.of(2026, 7, 22), "Nandos", "343.90", "Dining"));

        for (Row row : rows) {
            Transaction transaction = new Transaction();
            transaction.setUserId(userId);
            transaction.setSourceType(SourceType.STATEMENT);
            transaction.setDate(row.date());
            transaction.setMerchant(row.merchant());
            transaction.setAmount(new BigDecimal(row.amount()));
            transaction.setCategory(row.category());
            transaction.setPaymentMethod(PaymentMethod.CARD);
            transaction.setDirection(TransactionDirection.DEBIT);
            transaction.setStatus(TransactionStatus.ACTIVE);
            transactionRepository.save(transaction);
        }
    }
}
