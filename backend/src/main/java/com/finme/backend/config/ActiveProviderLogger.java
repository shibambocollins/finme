package com.finme.backend.config;

import com.finme.backend.ai.AiProvider;
import com.finme.backend.geocoding.GeocodingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Prints which AI and geocoding implementations actually won bean selection, once, at startup.
 * <p>
 * This exists because of a real and genuinely hard-to-spot failure: environment variables are
 * read once when the JVM starts, so editing backend/.env has no effect on an already-running
 * app. That produced a dashboard full of mock data - every map pin on one hardcoded Sandton
 * coordinate - while the configuration on disk said otherwise, with nothing in the logs to
 * contradict it. A running app should be able to answer "am I using real providers?" without
 * anyone having to read a config file and infer.
 */
@Component
public class ActiveProviderLogger {

    private static final Logger log = LoggerFactory.getLogger(ActiveProviderLogger.class);

    private final AiProvider aiProvider;
    private final GeocodingProvider geocodingProvider;

    public ActiveProviderLogger(AiProvider aiProvider, GeocodingProvider geocodingProvider) {
        this.aiProvider = aiProvider;
        this.geocodingProvider = geocodingProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logActiveProviders() {
        String ai = aiProvider.getClass().getSimpleName();
        String geocoding = geocodingProvider.getClass().getSimpleName();

        log.info("Active AI provider       : {}", ai);
        log.info("Active geocoding provider: {}", geocoding);

        if (ai.startsWith("Mock") || geocoding.startsWith("Mock")) {
            log.warn("");
            log.warn("  *** MOCK PROVIDERS ARE ACTIVE - DASHBOARD FIGURES ARE NOT REAL ***");
            if (ai.startsWith("Mock")) {
                log.warn("  AI        : every upload returns the same fixture transactions,");
                log.warn("              whatever the file actually contains. Set AI_PROVIDER=chain.");
            }
            if (geocoding.startsWith("Mock")) {
                log.warn("  Geocoding : every location resolves to one fixed coordinate in Sandton,");
                log.warn("              whatever the merchant is. Set GEOCODING_PROVIDER=opencage.");
            }
            log.warn("  Environment variables are read at JVM start - if you just edited");
            log.warn("  backend/.env, this app must be restarted to pick it up.");
            log.warn("");
        }
    }
}
