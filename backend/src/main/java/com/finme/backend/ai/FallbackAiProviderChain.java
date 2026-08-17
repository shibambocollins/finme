package com.finme.backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * NFR-7: falls back to the next provider on failure rather than surfacing an unhandled error.
 * Pure orchestration, no HTTP of its own - deliberately just try-in-order-then-give-up, no
 * retry-with-backoff or circuit breaker. Unit-tested against fake AiProvider stubs, no real
 * network calls or quota spent.
 */
public class FallbackAiProviderChain implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackAiProviderChain.class);

    private final List<AiProvider> providers;

    public FallbackAiProviderChain(List<AiProvider> providers) {
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("providers must not be empty");
        }
        this.providers = providers;
    }

    @Override
    public List<ExtractedTransaction> structureTransactions(String redactedText) {
        AiProviderException lastFailure = null;
        for (AiProvider provider : providers) {
            try {
                return provider.structureTransactions(redactedText);
            } catch (AiProviderException ex) {
                log.warn("AI provider {} failed, falling back to next in chain: {}",
                        provider.getClass().getSimpleName(), ex.getMessage());
                lastFailure = ex;
            }
        }
        throw new AllAiProvidersFailedException(lastFailure);
    }
}
