package com.finme.backend.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Verified against opencagedata.com/api docs, 2026-08-20: GET
 * https://api.opencagedata.com/geocode/v1/json?q=...&key=...&limit=1, response has
 * results[0].geometry.{lat,lng}. Any failure (network, non-2xx, empty results, malformed
 * body) yields Optional.empty() rather than an exception - a geocoding miss should never fail
 * the receipt ingestion it's enriching, matching the GeocodingProvider contract.
 */
public class OpenCageGeocodingProvider implements GeocodingProvider {

    private static final String BASE_URL = "https://api.opencagedata.com/geocode/v1/json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String apiKey;

    public OpenCageGeocodingProvider(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Override
    public Optional<GeocodeResult> geocode(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }

        try {
            // Template placeholders (not string concatenation) so RestClient URL-encodes the
            // address - it may contain spaces, commas, or other characters unsafe in a raw URL.
            String responseBody = restClient.get()
                    .uri(BASE_URL + "?q={q}&key={key}&limit={limit}", address, apiKey, 1)
                    .retrieve()
                    .body(String.class);
            return parseFirstResult(responseBody);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<GeocodeResult> parseFirstResult(String responseBody) {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode firstResult = root.at("/results/0");
            if (firstResult.isMissingNode()) {
                return Optional.empty();
            }
            JsonNode geometry = firstResult.get("geometry");
            if (geometry == null || !geometry.has("lat") || !geometry.has("lng")) {
                return Optional.empty();
            }
            return Optional.of(new GeocodeResult(geometry.get("lat").asDouble(), geometry.get("lng").asDouble()));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
