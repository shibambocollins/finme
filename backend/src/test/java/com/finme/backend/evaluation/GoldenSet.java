package com.finme.backend.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finme.backend.evaluation.GoldenDocument.GoldenTransaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Supplies the labeled documents the harness scores against, preferring real ones.
 * <p>
 * Real bank statements and receipt photos are the documents that actually matter (FR-1.9.1,
 * FR-1.9.2) - but they carry real financial data and cannot be committed to a public portfolio
 * repository. So the harness looks for them in an ignored directory and quietly falls back to
 * the synthetic set when they are absent. Adding real documents is a file-drop, not a code
 * change, and the report always states which set produced the numbers so a synthetic score is
 * never mistaken for a real-world one.
 *
 * <pre>
 * backend/src/test/resources/golden/statements/anything.json
 * {
 *   "source": "my-july-statement.pdf",
 *   "transactions": [
 *     {"date": "2026-07-02", "merchant": "WOOLWORTHS", "amount": "842.15", "category": "Groceries"}
 *   ]
 * }
 * </pre>
 */
final class GoldenSet {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path ROOT = Path.of("src", "test", "resources", "golden");

    private GoldenSet() {
    }

    record Loaded(List<GoldenDocument> documents, boolean synthetic) {
    }

    static Loaded statements() throws IOException {
        List<GoldenDocument> real = loadFrom(ROOT.resolve("statements"), "application/pdf");
        return real.isEmpty() ? new Loaded(SyntheticGoldenSet.statements(), true) : new Loaded(real, false);
    }

    static Loaded receipts() throws IOException {
        List<GoldenDocument> real = loadFrom(ROOT.resolve("receipts"), "image/jpeg");
        return real.isEmpty() ? new Loaded(SyntheticGoldenSet.receipts(), true) : new Loaded(real, false);
    }

    private static List<GoldenDocument> loadFrom(Path directory, String defaultMimeType) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        List<GoldenDocument> documents = new ArrayList<>();
        try (Stream<Path> labels = Files.list(directory)) {
            for (Path labelFile : labels.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonNode root = MAPPER.readTree(Files.readAllBytes(labelFile));
                Path source = directory.resolve(root.get("source").asText());
                if (!Files.exists(source)) {
                    throw new IllegalStateException(
                            "Golden label " + labelFile + " points at a missing source file: " + source);
                }

                List<GoldenTransaction> expected = new ArrayList<>();
                for (JsonNode node : root.get("transactions")) {
                    expected.add(new GoldenTransaction(
                            LocalDate.parse(node.get("date").asText()),
                            node.get("merchant").asText(),
                            // Read as text, never as a JSON number - a monetary amount routed
                            // through a double is exactly the precision loss this app avoids
                            // everywhere else, and the golden set is the last place that should
                            // introduce it.
                            new BigDecimal(node.get("amount").asText()),
                            node.has("category") ? node.get("category").asText() : null));
                }

                String mimeType = root.has("mimeType") ? root.get("mimeType").asText() : defaultMimeType;
                documents.add(new GoldenDocument(
                        source.getFileName().toString(), Files.readAllBytes(source), mimeType, expected));
            }
        }
        return documents;
    }
}
