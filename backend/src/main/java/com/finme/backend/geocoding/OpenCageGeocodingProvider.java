package com.finme.backend.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.Set;

/**
 * Verified against opencagedata.com/api docs and against the live API, 2026-08-21: GET
 * https://api.opencagedata.com/geocode/v1/json?q=...&key=...&limit=1, response has
 * results[0].geometry.{lat,lng}. Any failure (network, non-2xx, empty results, malformed
 * body) yields Optional.empty() rather than an exception - a geocoding miss should never fail
 * the ingestion it's enriching, matching the GeocodingProvider contract.
 * <p>
 * The country restriction and the result-type filter below are both there because a geocoder
 * asked to resolve a shop name will always answer with <em>something</em>. Measured live
 * against the merchant strings this app actually produces:
 * <ul>
 *   <li>"NANDOS MELROSE ARCH, Melrose Arch" returned a place in <b>Switzerland</b>, at
 *       confidence 8 of 10. Restricting to the configured country turns that into no result,
 *       which is the correct answer.</li>
 *   <li>"KFC V&amp;A WATERFRONT, V&amp;A Waterfront" returned _type "country" - the centroid of
 *       South Africa - also at <b>confidence 9</b>. Confidence therefore cannot be used to
 *       judge whether a result is usable: it reflects how tightly the match is bounded, not
 *       whether the right thing was matched. _type is the signal that actually separates
 *       "found the shop" from "gave up and returned the country".</li>
 * </ul>
 */
public class OpenCageGeocodingProvider implements GeocodingProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenCageGeocodingProvider.class);

    private static final String BASE_URL = "https://api.opencagedata.com/geocode/v1/json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Result types too coarse to mean anything on a spend map. A pin at the centroid of South
     * Africa, or of a 64km-wide metropolitan municipality, tells the user nothing and actively
     * misleads - it looks like a located transaction. Dropping these leaves the transaction
     * ungeocoded, which the map already handles.
     * <p>
     * "city" is deliberately NOT here: a suburb or town centroid (Rivonia, Rosebank, Fourways
     * all come back as _type "city") is genuinely useful at suburb level, and is exactly what
     * the approximate flag exists to communicate.
     */
    private static final Set<String> TOO_COARSE = Set.of(
            "continent", "country", "state", "province", "region", "county",
            "municipality", "local_administrative_area");

    private final RestClient restClient;
    private final String apiKey;
    private final String countryCode;

    public OpenCageGeocodingProvider(RestClient restClient, String apiKey, String countryCode) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.countryCode = countryCode;
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
                    .uri(BASE_URL + "?q={q}&key={key}&limit={limit}&countrycode={countrycode}",
                            address, apiKey, 1, countryCode)
                    .retrieve()
                    .body(String.class);
            return parseFirstResult(responseBody, address);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<GeocodeResult> parseFirstResult(String responseBody, String query) {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode firstResult = root.at("/results/0");
            if (firstResult.isMissingNode()) {
                return Optional.empty();
            }

            String type = firstResult.at("/components/_type").asText("");
            if (TOO_COARSE.contains(type)) {
                log.debug("Discarding geocode for '{}': matched only a '{}', too coarse to map", query, type);
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
