package com.finme.backend.service;

import com.finme.backend.ai.AiProvider;
import com.finme.backend.ai.AllAiProvidersFailedException;
import com.finme.backend.dto.RecommendationsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Produces the dashboard's AI-generated recommendations (FR-1.7.4).
 * <p>
 * The division of labour is the whole point: {@link SpendAnalysisService} calculates every
 * figure in code, and the AI is handed the finished summary purely to interpret and prioritise
 * it (FR-2.2.1). No transaction ever reaches the model from here, and no arithmetic is asked of
 * it.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private static final String NO_DATA =
            "Upload a statement or receipt to get recommendations.";
    private static final String UNAVAILABLE =
            "Recommendations are unavailable right now - the AI providers could not be reached.";

    private final SpendAnalysisService spendAnalysisService;
    private final AiProvider aiProvider;

    /**
     * Recommendations keyed by user, held against the exact facts they describe.
     * <p>
     * The dashboard requests these on every load, but the underlying spending changes only when
     * something is uploaded - and on the free tiers this project runs on, an AI call per page
     * load would exhaust a per-minute token budget that statement extraction also needs. Keying
     * the entry on the SpendFacts record means the cache invalidates itself the moment any
     * figure changes, with no expiry to tune and no staleness to reason about.
     * <p>
     * Deliberately in memory: these are cheap to regenerate and worthless to preserve across a
     * restart. If Iteration 6's weekly email needs them durably, that wants a table, not this.
     */
    private final Map<Long, CachedRecommendations> cache = new ConcurrentHashMap<>();

    private record CachedRecommendations(SpendFacts facts, List<String> recommendations) {
    }

    public RecommendationService(SpendAnalysisService spendAnalysisService, AiProvider aiProvider) {
        this.spendAnalysisService = spendAnalysisService;
        this.aiProvider = aiProvider;
    }

    public RecommendationsResponse getRecommendations(Long userId) {
        SpendFacts facts = spendAnalysisService.factsFor(userId);
        if (facts.isEmpty()) {
            return new RecommendationsResponse(List.of(), NO_DATA);
        }

        CachedRecommendations cached = cache.get(userId);
        if (cached != null && cached.facts().equals(facts)) {
            return new RecommendationsResponse(cached.recommendations(), null);
        }

        try {
            List<String> recommendations = aiProvider.recommend(facts.asPromptText());
            cache.put(userId, new CachedRecommendations(facts, recommendations));
            return new RecommendationsResponse(recommendations, null);
        } catch (AllAiProvidersFailedException ex) {
            // Recommendations are commentary on figures the dashboard already shows correctly.
            // Losing them should degrade the page, never fail it - the charts and totals below
            // are computed in code and remain valid whatever the AI providers are doing.
            log.warn("Recommendations unavailable for user {}: {}", userId, ex.getMessage());
            return new RecommendationsResponse(List.of(), UNAVAILABLE);
        }
    }
}
