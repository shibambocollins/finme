package com.finme.backend.config;

import com.finme.backend.ai.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Prints which AI implementation actually won bean selection, once, at startup.
 * <p>
 * This exists because of a real and genuinely hard-to-spot failure: environment variables are
 * read once when the JVM starts, so editing backend/.env has no effect on an already-running
 * app. That produced a dashboard full of fixture data while the configuration on disk said
 * otherwise, with nothing in the logs to contradict it. A running app should be able to answer
 * "am I using a real provider?" without anyone having to read a config file and infer.
 */
@Component
public class ActiveProviderLogger {

    private static final Logger log = LoggerFactory.getLogger(ActiveProviderLogger.class);

    private final AiProvider aiProvider;

    public ActiveProviderLogger(AiProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logActiveProviders() {
        String ai = aiProvider.getClass().getSimpleName();
        log.info("Active AI provider: {}", ai);

        if (ai.startsWith("Mock")) {
            log.warn("");
            log.warn("  *** MOCK AI PROVIDER IS ACTIVE - DASHBOARD FIGURES ARE NOT REAL ***");
            log.warn("  Every upload returns the same fixture transactions, whatever the file");
            log.warn("  actually contains. Set AI_PROVIDER=chain for real extraction.");
            log.warn("  Environment variables are read at JVM start - if you just edited");
            log.warn("  backend/.env, this app must be restarted to pick it up.");
            log.warn("");
        }
    }
}
