package com.finme.backend.geocoding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Deterministic stand-in for a real geocoding call - same role and same "default even with a
 * real key present" reasoning as com.finme.backend.ai.MockAiProvider: switching to a real
 * provider spends real API quota on every receipt upload, so that should be a conscious choice
 * (geocoding.provider=opencage), not the default.
 */
@Component
@Primary
@ConditionalOnProperty(name = "geocoding.provider", havingValue = "mock", matchIfMissing = true)
public class MockGeocodingProvider implements GeocodingProvider {

    /** Sandton, Johannesburg - an arbitrary but real, stable coordinate for mock results. */
    private static final GeocodeResult FIXED_RESULT = new GeocodeResult(-26.1076, 28.0567);

    @Override
    public Optional<GeocodeResult> geocode(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(FIXED_RESULT);
    }
}
