package com.finme.backend.geocoding;

import java.util.Optional;

/**
 * A single provider, not a fallback chain like com.finme.backend.ai.AiProvider - geocoding is
 * best-effort map enrichment, not a required extraction step, so there is no product need to
 * pay for a second provider's coverage. A failed or unmatched lookup returns Optional.empty(),
 * never an exception - the caller (ReceiptIngestionService) treats "not geocoded" the same as
 * "receipt had no printed address", matching docs/03-system-design.md's "where available" scope
 * for map coordinates.
 */
public interface GeocodingProvider {

    Optional<GeocodeResult> geocode(String address);
}
