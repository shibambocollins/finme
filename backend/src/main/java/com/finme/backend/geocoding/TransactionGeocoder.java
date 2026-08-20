package com.finme.backend.geocoding;

import com.finme.backend.ai.ExtractedTransaction;
import com.finme.backend.entity.Transaction;
import org.springframework.stereotype.Service;

/**
 * Shared enrichment step for both ReceiptIngestionService and StatementIngestionService - the
 * "resolve whatever location text the AI extracted into coordinates" logic is identical for
 * both, only the source text differs.
 * <p>
 * Two tiers, in order: a receipt's exact printed address geocodes on its own (precise); failing
 * that, "merchant, locationHint" - a city/suburb/place name the source text already carried
 * (a card statement's merchant line, or a receipt with no full address) - geocodes as an
 * approximate stand-in, since a bare merchant name alone (e.g. "KFC") could resolve anywhere in
 * the country. A miss at either tier just leaves the transaction ungeocoded; it never fails
 * ingestion.
 */
@Service
public class TransactionGeocoder {

    private final GeocodingProvider geocodingProvider;

    public TransactionGeocoder(GeocodingProvider geocodingProvider) {
        this.geocodingProvider = geocodingProvider;
    }

    public void enrich(Transaction transaction, ExtractedTransaction extracted) {
        if (extracted.address() != null) {
            transaction.setAddress(extracted.address());
            geocodingProvider.geocode(extracted.address())
                    .ifPresent(result -> apply(transaction, result, false));
            return;
        }

        if (extracted.locationHint() != null) {
            String query = transaction.getMerchant() + ", " + extracted.locationHint();
            geocodingProvider.geocode(query)
                    .ifPresent(result -> apply(transaction, result, true));
        }
    }

    private void apply(Transaction transaction, GeocodeResult result, boolean approximate) {
        transaction.setLatitude(result.latitude());
        transaction.setLongitude(result.longitude());
        transaction.setLocationApproximate(approximate);
    }
}
