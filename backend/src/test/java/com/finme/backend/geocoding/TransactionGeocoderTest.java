package com.finme.backend.geocoding;

import com.finme.backend.ai.ExtractedTransaction;
import com.finme.backend.entity.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionGeocoderTest {

    private final GeocodingProvider geocodingProvider = mock(GeocodingProvider.class);
    private final TransactionGeocoder transactionGeocoder = new TransactionGeocoder(geocodingProvider);

    private static Transaction transactionWithMerchant(String merchant) {
        Transaction transaction = new Transaction();
        transaction.setMerchant(merchant);
        return transaction;
    }

    private static ExtractedTransaction extracted(String address, String locationHint) {
        return new ExtractedTransaction(
                LocalDate.of(2026, 1, 1), "KFC", new BigDecimal("75.00"), "Dining", "desc",
                "CARD", address, locationHint);
    }

    @Test
    void prefersAnExactAddressOverALocationHint() {
        Transaction transaction = transactionWithMerchant("KFC");
        when(geocodingProvider.geocode("1 Long St")).thenReturn(Optional.of(new GeocodeResult(-33.9, 18.4)));

        transactionGeocoder.enrich(transaction, extracted("1 Long St", "Cape Town CBD"));

        verify(geocodingProvider, never()).geocode("KFC, Cape Town CBD");
        assertThat(transaction.getAddress()).isEqualTo("1 Long St");
        assertThat(transaction.getLatitude()).isEqualTo(-33.9);
        assertThat(transaction.getLongitude()).isEqualTo(18.4);
        assertThat(transaction.isLocationApproximate()).isFalse();
    }

    @Test
    void fallsBackToMerchantAndLocationHintWhenThereIsNoAddress() {
        Transaction transaction = transactionWithMerchant("KFC");
        when(geocodingProvider.geocode("KFC, Cape Town CBD"))
                .thenReturn(Optional.of(new GeocodeResult(-33.9249, 18.4241)));

        transactionGeocoder.enrich(transaction, extracted(null, "Cape Town CBD"));

        assertThat(transaction.getAddress()).isNull();
        assertThat(transaction.getLatitude()).isEqualTo(-33.9249);
        assertThat(transaction.getLongitude()).isEqualTo(18.4241);
        assertThat(transaction.isLocationApproximate()).isTrue();
    }

    @Test
    void leavesTransactionUngeocodedWhenNeitherAddressNorLocationHintArePresent() {
        Transaction transaction = transactionWithMerchant("KFC");

        transactionGeocoder.enrich(transaction, extracted(null, null));

        assertThat(transaction.getLatitude()).isNull();
        assertThat(transaction.getLongitude()).isNull();
        assertThat(transaction.isLocationApproximate()).isFalse();
    }

    @Test
    void leavesTransactionUngeocodedWhenTheProviderMissesOnTheLocationHintFallback() {
        Transaction transaction = transactionWithMerchant("KFC");
        when(geocodingProvider.geocode("KFC, Nowhereville")).thenReturn(Optional.empty());

        transactionGeocoder.enrich(transaction, extracted(null, "Nowhereville"));

        assertThat(transaction.getLatitude()).isNull();
        assertThat(transaction.getLongitude()).isNull();
    }
}
