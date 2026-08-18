package com.finme.backend.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Local-only text extraction (FR-1.2.2) - the raw PDF bytes never leave the backend process,
 * let alone reach an external AI provider.
 */
@Service
public class PdfExtractionService {

    public String extractText(InputStream pdfInputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfInputStream.readAllBytes())) {
            return new PDFTextStripper().getText(document);
        }
    }
}
