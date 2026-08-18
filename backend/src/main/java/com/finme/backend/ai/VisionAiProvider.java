package com.finme.backend.ai;

import java.util.List;

/**
 * The receipt-photo counterpart to AiProvider - a separate interface, not an overload, since
 * a receipt has no local text-extraction step to feed a shared method (docs/03-system-design.md:
 * "a separate pipeline from statement ingestion, since there is no local text-extraction step
 * available for a photo"). Reuses ExtractedTransaction - a receipt produces the same shape of
 * data a statement transaction does.
 */
public interface VisionAiProvider {

    List<ExtractedTransaction> extractFromImage(byte[] imageBytes, String mimeType);
}
