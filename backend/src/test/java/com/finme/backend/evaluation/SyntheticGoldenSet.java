package com.finme.backend.evaluation;

import com.finme.backend.evaluation.GoldenDocument.GoldenTransaction;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The golden set the harness scores against (FR-1.9.1, FR-1.9.2).
 * <p>
 * <b>These documents are synthetic, and that is a real limitation to state plainly.</b> They are
 * generated, so their labels are exact by construction and the harness runs today with nothing
 * to install - but a rendered statement is clean in ways a real one is not: no scanner skew, no
 * multi-column drift, no bank-specific layout quirks, no faded thermal print. Scores here
 * measure the pipeline's handling of well-formed input, which is a floor on difficulty, not a
 * sample of it.
 * <p>
 * The documents deliberately include the shapes that have actually broken this pipeline: an
 * income line and a refund (direction handling), a statement long enough to need several chunks,
 * and a non-obvious date format. Each is a regression that cost real debugging time.
 * <p>
 * {@link GoldenSet} prefers labeled real documents whenever they are present, so replacing this
 * with genuine statements is a matter of dropping files in - no code change.
 */
final class SyntheticGoldenSet {

    private SyntheticGoldenSet() {
    }

    static List<GoldenDocument> statements() throws IOException {
        return List.of(everydayStatement(), incomeAndRefundStatement(), multiChunkStatement(), slashDateStatement());
    }

    static List<GoldenDocument> receipts() throws IOException {
        return List.of(groceryReceipt(), restaurantReceipt(), fuelReceipt());
    }

    // ------------------------------------------------------------------ statements

    private static GoldenDocument everydayStatement() throws IOException {
        List<String> lines = List.of(
                "STANDARD BANK - Cheque Account Statement",
                "Statement Period: 01 July 2026 to 31 July 2026",
                "Date     Description                        Debit     Credit",
                "02 Jul   WOOLWORTHS SANDTON CITY             842.15",
                "03 Jul   UBER TRIP CAPE TOWN                  87.50",
                "07 Jul   NETFLIX SUBSCRIPTION                199.00",
                "11 Jul   CITY OF JHB ELECTRICITY             900.00",
                "14 Jul   CLICKS PHARMACY ROSEBANK            243.75",
                "22 Jul   NANDOS MELROSE ARCH                 215.00");

        List<GoldenTransaction> expected = List.of(
                label(2026, 7, 2, "WOOLWORTHS SANDTON CITY", "842.15", "Groceries"),
                label(2026, 7, 3, "UBER TRIP CAPE TOWN", "87.50", "Transport"),
                label(2026, 7, 7, "NETFLIX SUBSCRIPTION", "199.00", "Entertainment"),
                label(2026, 7, 11, "CITY OF JHB ELECTRICITY", "900.00", "Utilities"),
                label(2026, 7, 14, "CLICKS PHARMACY ROSEBANK", "243.75", "Health"),
                label(2026, 7, 22, "NANDOS MELROSE ARCH", "215.00", "Dining"));

        return new GoldenDocument("statement-everyday", pdf(lines), "application/pdf", expected);
    }

    /**
     * The shape that made "total spend" read 27,658.15 against a true 7,473.85. Both money-in
     * rows are labeled, because extraction should find them - it is the dashboard's job to
     * exclude income and subtract refunds, not the extractor's to hide them.
     */
    private static GoldenDocument incomeAndRefundStatement() throws IOException {
        List<String> lines = List.of(
                "STANDARD BANK - Cheque Account Statement",
                "Statement Period: 01 July 2026 to 31 July 2026",
                "Date     Description                        Debit     Credit",
                "01 Jul   SALARY ACB CREDIT MONTHLY                    18500.00",
                "02 Jul   WOOLWORTHS SANDTON CITY             842.15",
                "15 Jul   REFUND WOOLWORTHS SANDTON CITY                 842.15",
                "17 Jul   CHECKERS HYPER FOURWAYS            1256.40",
                "31 Jul   BANK CHARGES MONTHLY FEE            115.00");

        List<GoldenTransaction> expected = List.of(
                label(2026, 7, 1, "SALARY ACB CREDIT MONTHLY", "18500.00", "Income"),
                label(2026, 7, 2, "WOOLWORTHS SANDTON CITY", "842.15", "Groceries"),
                label(2026, 7, 15, "REFUND WOOLWORTHS SANDTON CITY", "842.15", "Groceries"),
                label(2026, 7, 17, "CHECKERS HYPER FOURWAYS", "1256.40", "Groceries"),
                label(2026, 7, 31, "BANK CHARGES MONTHLY FEE", "115.00", "Other"));

        return new GoldenDocument("statement-income-and-refund", pdf(lines), "application/pdf", expected);
    }

    /** Long enough to force several chunks - the path where 15 of 16 rows once vanished silently. */
    private static GoldenDocument multiChunkStatement() throws IOException {
        String[] merchants = {
                "WOOLWORTHS SANDTON", "UBER TRIP", "SHELL GARAGE", "NETFLIX SUBSCRIPTION",
                "KFC WATERFRONT", "CITY OF JHB ELECTRICITY", "CLICKS PHARMACY", "CHECKERS HYPER"};
        String[] categories = {
                "Groceries", "Transport", "Transport", "Entertainment",
                "Dining", "Utilities", "Health", "Groceries"};

        List<String> lines = new ArrayList<>(List.of(
                "STANDARD BANK - Cheque Account Statement",
                "Statement Period: 01 July 2026 to 31 July 2026",
                "Date     Description                        Debit     Credit"));
        List<GoldenTransaction> expected = new ArrayList<>();

        for (int i = 0; i < 32; i++) {
            int day = (i % 28) + 1;
            BigDecimal amount = new BigDecimal(String.format("%d.%02d", 100 + i * 7, (i * 13) % 100));
            String merchant = merchants[i % merchants.length];
            lines.add(String.format("%02d Jul   %-34s %8s", day, merchant, amount));
            expected.add(new GoldenTransaction(
                    LocalDate.of(2026, 7, day), merchant, amount, categories[i % categories.length]));
        }

        return new GoldenDocument("statement-multi-chunk", pdf(lines), "application/pdf", expected);
    }

    /** dd/mm/yyyy rather than "02 Jul" - a format where a model can silently swap day and month. */
    private static GoldenDocument slashDateStatement() throws IOException {
        List<String> lines = List.of(
                "ABSA BANK - Credit Card Statement",
                "Statement Period: 01/07/2026 to 31/07/2026",
                "Date         Description                    Amount",
                "04/07/2026   PICK N PAY MENLYN               978.30",
                "09/07/2026   GAUTRAIN CARD RECHARGE          300.00",
                "12/07/2026   DISCOVERY HEALTH PREMIUM       2150.00",
                "27/07/2026   VODACOM PREPAID AIRTIME         250.00");

        List<GoldenTransaction> expected = List.of(
                label(2026, 7, 4, "PICK N PAY MENLYN", "978.30", "Groceries"),
                label(2026, 7, 9, "GAUTRAIN CARD RECHARGE", "300.00", "Transport"),
                label(2026, 7, 12, "DISCOVERY HEALTH PREMIUM", "2150.00", "Health"),
                label(2026, 7, 27, "VODACOM PREPAID AIRTIME", "250.00", "Utilities"));

        return new GoldenDocument("statement-slash-dates", pdf(lines), "application/pdf", expected);
    }

    // ------------------------------------------------------------------ receipts

    private static GoldenDocument groceryReceipt() throws IOException {
        List<String> lines = List.of(
                "        WOOLWORTHS",
                "      Sandton City Mall",
                "",
                "  Date: 14/07/2026   Time: 16:42",
                "  ------------------------------",
                "  Milk 2L Full Cream       34.99",
                "  Brown Bread              22.50",
                "  Free Range Eggs 18       89.99",
                "  Chicken Breasts 1kg      74.90",
                "  Bananas 1kg              25.47",
                "  ------------------------------",
                "  TOTAL                   247.85",
                "  CARD PAYMENT            247.85");

        return new GoldenDocument("receipt-grocery", receiptImage(lines), "image/jpeg",
                List.of(label(2026, 7, 14, "WOOLWORTHS", "247.85", "Groceries")));
    }

    private static GoldenDocument restaurantReceipt() throws IOException {
        List<String> lines = List.of(
                "         NANDOS",
                "       Melrose Arch",
                "",
                "  Date: 22/07/2026   Table: 12",
                "  ------------------------------",
                "  1/4 Chicken Meal        129.00",
                "  Peri Chips               45.00",
                "  Still Water              22.00",
                "  ------------------------------",
                "  SUBTOTAL                196.00",
                "  Service Fee              19.00",
                "  TOTAL                   215.00",
                "  CASH                    215.00");

        return new GoldenDocument("receipt-restaurant", receiptImage(lines), "image/jpeg",
                List.of(label(2026, 7, 22, "NANDOS", "215.00", "Dining")));
    }

    private static GoldenDocument fuelReceipt() throws IOException {
        List<String> lines = List.of(
                "        SHELL RIVONIA",
                "     Rivonia Road, Sandton",
                "",
                "  Date: 05/07/2026   Pump: 3",
                "  ------------------------------",
                "  Unleaded 95      28.40 L",
                "  Price/L                  22.89",
                "  ------------------------------",
                "  TOTAL                   650.08",
                "  CARD PAYMENT            650.08");

        return new GoldenDocument("receipt-fuel", receiptImage(lines), "image/jpeg",
                List.of(label(2026, 7, 5, "SHELL RIVONIA", "650.08", "Transport")));
    }

    // ------------------------------------------------------------------ rendering

    private static GoldenTransaction label(int year, int month, int day, String merchant, String amount, String category) {
        return new GoldenTransaction(LocalDate.of(year, month, day), merchant, new BigDecimal(amount), category);
    }

    private static byte[] pdf(List<String> lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER), 9);
                content.beginText();
                content.newLineAtOffset(40, 780);
                for (String line : lines) {
                    content.showText(line);
                    content.newLineAtOffset(0, -13);
                }
                content.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] receiptImage(List<String> lines) throws IOException {
        int width = 620;
        int height = 80 + lines.size() * 32;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);

        int y = 50;
        for (String line : lines) {
            g.setFont(new Font(Font.MONOSPACED, line.contains("TOTAL") ? Font.BOLD : Font.PLAIN, 22));
            g.drawString(line, 30, y);
            y += 32;
        }
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
