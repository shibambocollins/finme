package com.finme.backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;

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
        return tryEachInTurn(provider -> provider.structureTransactions(redactedText));
    }

    @Override
    public List<String> recommend(String spendFactsSummary) {
        return tryEachInTurn(provider -> provider.recommend(spendFactsSummary));
    }

    private <T> T tryEachInTurn(Function<AiProvider, T> call) {
        AiProviderException lastFailure = null;
        for (AiProvider provider : providers) {
            try {
                return call.apply(provider);
            } catch (AiProviderException ex) {
                // Log the root cause, not just ex.getMessage(). A wrapped "Groq request
                // failed" told us nothing while the real answer - an HTTP 413 naming the
                // exact token limit - sat one level down in the cause chain.
                log.warn("AI provider {} failed, falling back to next in chain: {} [cause: {}]",
                        provider.getClass().getSimpleName(), ex.getMessage(), rootCauseOf(ex));
                lastFailure = ex;
            }
        }
        throw new AllAiProvidersFailedException(lastFailure);
    }

    private static String rootCauseOf(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
