package com.finme.backend.geocoding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * Builds the real OpenCageGeocodingProvider as the active GeocodingProvider bean when
 * geocoding.provider=opencage. MockGeocodingProvider keeps @Primary on its own
 * mutually-exclusive havingValue="mock" condition - same pattern as
 * com.finme.backend.ai.AiProviderConfig, so there's never ambiguity for a plain
 * GeocodingProvider constructor param.
 */
@Configuration
public class GeocodingConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "geocoding.provider", havingValue = "opencage")
    public GeocodingProvider openCageGeocodingProvider(
            RestClient.Builder restClientBuilder,
            @Value("${opencage.api-key}") String apiKey) {
        return new OpenCageGeocodingProvider(restClientBuilder.build(), apiKey);
    }
}
