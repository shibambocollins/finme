package com.finme.backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Vision counterpart to FallbackAiProviderChain - a deliberate small duplicate rather than a
 * shared generic abstraction, since AiProvider and VisionAiProvider have different method
 * signatures and this orchestration logic isn't worth the extra indirection for two call
 * sites. Same NFR-7 behavior: try in order, fall back on failure, give up only once every
 * provider has failed.
 */
public class FallbackVisionAiProviderChain implements VisionAiProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackVisionAiProviderChain.class);

    private final List<VisionAiProvider> providers;

    public FallbackVisionAiProviderChain(List<VisionAiProvider> providers) {
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("providers must not be empty");
        }
        this.providers = providers;
    }

    @Override
    public List<ExtractedTransaction> extractFromImage(byte[] imageBytes, String mimeType) {
        AiProviderException lastFailure = null;
        for (VisionAiProvider provider : providers) {
            try {
                return provider.extractFromImage(imageBytes, mimeType);
            } catch (AiProviderException ex) {
                log.warn("Vision AI provider {} failed, falling back to next in chain: {}",
                        provider.getClass().getSimpleName(), ex.getMessage());
                lastFailure = ex;
            }
        }
        throw new AllAiProvidersFailedException(lastFailure);
    }
}
