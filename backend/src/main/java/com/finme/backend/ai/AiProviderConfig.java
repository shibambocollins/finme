package com.finme.backend.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Builds the real fallback chain (Groq -> OpenRouter -> Cloudflare - matching every doc's
 * stated ordering, not yet empirically rate/latency-tested per docs/07-tech-stack.md) as the
 * active AiProvider bean when ai.provider=chain. MockAiProvider keeps @Primary on its own
 * mutually-exclusive havingValue="mock" condition, so there's never ambiguity for
 * StatementIngestionService's plain AiProvider constructor param - exactly one of the two
 * conditions is ever true.
 */
@Configuration
public class AiProviderConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "ai.provider", havingValue = "chain")
    public AiProvider chainAiProvider(
            RestClient.Builder restClientBuilder,
            @Value("${groq.api-key}") String groqApiKey,
            @Value("${groq.model}") String groqModel,
            @Value("${openrouter.api-key}") String openRouterApiKey,
            @Value("${openrouter.model}") String openRouterModel,
            @Value("${cloudflare.account-id}") String cloudflareAccountId,
            @Value("${cloudflare.api-token}") String cloudflareApiToken,
            @Value("${cloudflare.model}") String cloudflareModel) {

        RestClient restClient = restClientBuilder
                .requestFactory(timeoutRequestFactory())
                .build();

        List<AiProvider> providers = List.of(
                new GroqProvider(restClient, groqApiKey, groqModel),
                new OpenRouterProvider(restClient, openRouterApiKey, openRouterModel),
                new CloudflareProvider(restClient, cloudflareAccountId, cloudflareApiToken, cloudflareModel)
        );

        return new FallbackAiProviderChain(providers);
    }

    private SimpleClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return factory;
    }
}
