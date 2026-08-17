package com.finme.backend.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Builds a synthetic PDF for tests - not a real bank statement. There's no reason to use real
 * financial data to test a pipeline that (this iteration) ends in a mock AI provider anyway.
 */
final class SamplePdfFixture {

    static final String ACCOUNT_NUMBER = "1234567890";
    static final String ID_NUMBER = "9001015800086";

    private SamplePdfFixture() {
    }

    static byte[] buildSampleStatementPdf() throws IOException {
        List<String> lines = List.of(
                "FinMe Test Bank - Statement",
                "Account Holder Name: John Doe",
                "Account Number: " + ACCOUNT_NUMBER,
                "ID Number: " + ID_NUMBER,
                "Date         Merchant             Amount",
                "12/01/2026   Woolworths Sandton   R450.00",
                "14/01/2026   Uber                 R85.50"
        );

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                contentStream.setFont(font, 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(50, 750);
                for (String line : lines) {
                    contentStream.showText(line);
                    contentStream.newLineAtOffset(0, -20);
                }
                contentStream.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
