package com.finme.backend.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fast, targeted confirmation that OpenRouterProvider's flat 12000-token budget actually
 * resolves the truncation seen live on 2026-08-27 - calls OpenRouterProvider directly rather
 * than running a whole statement through the fallback chain, so this does not depend on (or
 * wait out) Groq's daily quota being exhausted first. Throwaway once it has served its purpose;
 * kept for now since re-running it is the cheapest way to notice a regression here specifically.
 *
 * <pre>./mvnw test -Dtest=OpenRouterBudgetProbe -Dlive.ai=true</pre>
 */
@EnabledIfSystemProperty(named = "live.ai", matches = "true")
class OpenRouterBudgetProbe {

    @Test
    void extractsAFullSizedChunkWithoutTruncating() {
        String apiKey = System.getenv("OPENROUTER_API_KEY");
        String model = System.getenv().getOrDefault("OPENROUTER_MODEL", "nvidia/nemotron-3-super-120b-a12b:free");
        assertThat(apiKey).as("OPENROUTER_API_KEY must be set for this probe").isNotBlank();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofSeconds(120));
        RestClient restClient = RestClient.builder().requestFactory(factory).build();
        OpenRouterProvider provider = new OpenRouterProvider(restClient, apiKey, model);

        // Same shape and merchant text as LiveExtractionSmokeTest#buildLargeStatementPdf - the
        // exact content that was truncating live, not a smaller or differently-worded stand-in.
        String[] merchants = {
                "WOOLWORTHS SANDTON CITY", "UBER TRIP CAPE TOWN", "SHELL GARAGE RIVONIA",
                "NETFLIX SUBSCRIPTION", "KFC V&A WATERFRONT", "CITY OF JHB ELECTRICITY",
                "CLICKS PHARMACY ROSEBANK", "CHECKERS HYPER FOURWAYS", "VODACOM PREPAID AIRTIME",
                "NANDOS MELROSE ARCH", "GAUTRAIN CARD RECHARGE", "DISCOVERY HEALTH PREMIUM",
                "PICK N PAY MENLYN", "BANK CHARGES MONTHLY FEE", "TAKEALOT ONLINE ORDER",
                "SPAR PARKTOWN NORTH"};
        Random random = new Random(7);
        StringBuilder statement = new StringBuilder(
                "STANDARD BANK - Cheque Account Statement\n"
                        + "Statement Period: 01 July 2026 to 31 July 2026\n"
                        + "Date     Description                        Debit     Credit\n");
        for (int i = 0; i < 40; i++) {
            double amount = 35 + random.nextDouble() * 2365;
            statement.append(String.format("%02d Jul   %-34s %8.2f%n",
                    (i % 28) + 1, merchants[i % merchants.length], amount));
        }

        List<ExtractedTransaction> extracted = provider.structureTransactions(statement.toString());

        System.out.println("\n===== OpenRouter budget probe =====");
        System.out.println("extracted: " + extracted.size() + "/40");
        System.out.println("====================================\n");

        assertThat(extracted).hasSize(40);
    }

    @Test
    void aTypicalManualEntryStillWorksWithTheSameFlatBudget() {
        String apiKey = System.getenv("OPENROUTER_API_KEY");
        String model = System.getenv().getOrDefault("OPENROUTER_MODEL", "nvidia/nemotron-3-super-120b-a12b:free");
        assertThat(apiKey).as("OPENROUTER_API_KEY must be set for this probe").isNotBlank();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofSeconds(120));
        RestClient restClient = RestClient.builder().requestFactory(factory).build();
        OpenRouterProvider provider = new OpenRouterProvider(restClient, apiKey, model);

        List<ExtractedTransaction> extracted =
                provider.parseManualEntry("lunch R150 cash today", LocalDate.of(2026, 7, 20));

        assertThat(extracted).hasSize(1);
        assertThat(extracted.get(0).amount()).isEqualByComparingTo("150.00");
    }
}
