package com.finme.backend.evaluation;

import com.finme.backend.ai.AiProvider;
import com.finme.backend.ai.ExtractedTransaction;
import com.finme.backend.ai.VisionAiProvider;
import com.finme.backend.service.PdfExtractionService;
import com.finme.backend.service.RedactionService;
import com.finme.backend.service.StatementTextChunker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The extraction evaluation harness (FR-1.9.3): runs both pipelines over the golden set against
 * real providers and reports precision, recall, category accuracy and hallucination rate,
 * scored separately for PDF and photo.
 *
 * <pre>./mvnw test -Dtest=ExtractionEvaluationHarnessTest -Dlive.ai=true</pre>
 *
 * Gated behind that flag because it spends real API quota. The report is printed and written to
 * {@code target/extraction-evaluation.txt} so runs can be compared over time - a single accuracy
 * number is only meaningful next to the previous one.
 * <p>
 * It measures the extraction path exactly as production runs it - the same PDF text extraction,
 * redaction, chunking and provider chain - but calls them directly rather than through
 * StatementIngestionService, so the numbers are about extraction rather than about persistence
 * or background scheduling.
 */
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
class ExtractionEvaluationHarnessTest {

    /**
     * A deliberately low bar. This is a measuring instrument, not a quality gate - pinning a
     * high threshold against free-tier models would turn ordinary provider variance into red
     * builds and train everyone to ignore it. It exists only to catch total collapse: a chain
     * returning nothing, or every document failing.
     */
    private static final double MINIMUM_USABLE_RECALL = 0.5;

    @Autowired
    private AiProvider aiProvider;

    @Autowired
    private VisionAiProvider visionAiProvider;

    @Autowired
    private PdfExtractionService pdfExtractionService;

    @Autowired
    private RedactionService redactionService;

    @Autowired
    private StatementTextChunker statementTextChunker;

    @Test
    void measuresBothPipelinesAgainstTheGoldenSet() throws IOException {
        GoldenSet.Loaded statements = GoldenSet.statements();
        GoldenSet.Loaded receipts = GoldenSet.receipts();

        ExtractionMetrics pdfMetrics = evaluate("PDF (statements)", statements,
                document -> extractFromStatement(document.bytes()));
        ExtractionMetrics photoMetrics = evaluate("Photo (receipts)", receipts,
                document -> visionAiProvider.extractFromImage(document.bytes(), document.mimeType()));

        String report = buildReport(statements, receipts, pdfMetrics, photoMetrics);
        System.out.println(report);
        Path output = Path.of("target", "extraction-evaluation.txt");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report);
        System.out.println("report written to " + output.toAbsolutePath());

        assertThat(pdfMetrics.recall())
                .as("PDF pipeline recall collapsed - see the report above")
                .isGreaterThanOrEqualTo(MINIMUM_USABLE_RECALL);
        assertThat(photoMetrics.recall())
                .as("Photo pipeline recall collapsed - see the report above")
                .isGreaterThanOrEqualTo(MINIMUM_USABLE_RECALL);
    }

    /** Production's statement path: extract text locally, redact, chunk, then one call per chunk. */
    private List<ExtractedTransaction> extractFromStatement(byte[] pdfBytes) throws IOException {
        String redacted = redactionService.redact(
                pdfExtractionService.extractText(new ByteArrayInputStream(pdfBytes)));
        List<ExtractedTransaction> extracted = new ArrayList<>();
        for (String chunk : statementTextChunker.chunk(redacted)) {
            extracted.addAll(aiProvider.structureTransactions(chunk));
        }
        return extracted;
    }

    private interface Extractor {
        List<ExtractedTransaction> extract(GoldenDocument document) throws IOException;
    }

    private ExtractionMetrics evaluate(String pipeline, GoldenSet.Loaded loaded, Extractor extractor) {
        int truePositives = 0;
        int falsePositives = 0;
        int falseNegatives = 0;
        int categoryHits = 0;
        List<String> missed = new ArrayList<>();
        List<String> invented = new ArrayList<>();
        List<String> miscategorised = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (GoldenDocument document : loaded.documents()) {
            List<ExtractedTransaction> actual;
            try {
                actual = extractor.extract(document);
            } catch (Exception ex) {
                // A document the chain could not process at all is recorded as a total miss
                // rather than skipped. Dropping it would quietly raise the average by removing
                // the hardest cases - the opposite of what an honest harness should do.
                failures.add(document.name() + ": " + ex.getMessage());
                falseNegatives += document.expected().size();
                missed.addAll(document.expected().stream()
                        .map(e -> document.name() + ": " + e.date() + " " + e.merchant() + " " + e.amount())
                        .toList());
                continue;
            }

            ExtractionEvaluator.Result result =
                    ExtractionEvaluator.score(document.name(), document.expected(), actual);
            truePositives += result.truePositives;
            falsePositives += result.falsePositives;
            falseNegatives += result.falseNegatives;
            categoryHits += result.categoryHits;
            missed.addAll(result.missed);
            invented.addAll(result.invented);
            miscategorised.addAll(result.miscategorised);
        }

        return new ExtractionMetrics(pipeline, loaded.documents().size(), truePositives, falsePositives,
                falseNegatives, categoryHits, missed, invented, miscategorised, failures);
    }

    private static String buildReport(GoldenSet.Loaded statements, GoldenSet.Loaded receipts,
                                      ExtractionMetrics pdf, ExtractionMetrics photo) {
        StringBuilder report = new StringBuilder();
        report.append("FinMe extraction evaluation\n");
        report.append("run at: ").append(java.time.Instant.now()).append('\n');
        report.append("statement golden set: ").append(describe(statements)).append('\n');
        report.append("receipt golden set  : ").append(describe(receipts)).append('\n');
        if (statements.synthetic() || receipts.synthetic()) {
            report.append("""

                    NOTE: synthetic documents are generated and therefore clean - no scan skew,
                    no column drift, no faded thermal print. These scores are a floor on
                    difficulty, not a sample of real-world difficulty. Drop labeled real
                    documents into src/test/resources/golden/ to measure what actually matters.
                    """);
        }
        report.append(pdf.render());
        report.append(photo.render());
        return report.toString();
    }

    private static String describe(GoldenSet.Loaded loaded) {
        return loaded.documents().size() + " documents (" + (loaded.synthetic() ? "SYNTHETIC" : "real, labeled") + ")";
    }
}
