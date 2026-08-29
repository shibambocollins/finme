package com.finme.backend.service;

import com.finme.backend.dto.RecommendationsResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Composes the weekly spend analysis (FR-1.8.1).
 * <p>
 * The figures come from {@link SpendAnalysisService} - the same deterministic arithmetic the
 * dashboard shows, so an email and the app can never disagree about what was spent. The
 * recommendations are narration layered on top, and are genuinely optional: if the AI providers
 * are unreachable the email still goes out with the numbers, because the numbers are the point
 * and they were computed locally. An email that never arrives because a third party was rate
 * limited is a worse outcome than one without its closing paragraph.
 */
@Service
public class WeeklySpendAnalysisService {

    private final SpendAnalysisService spendAnalysisService;
    private final RecommendationService recommendationService;

    public WeeklySpendAnalysisService(SpendAnalysisService spendAnalysisService,
                                      RecommendationService recommendationService) {
        this.spendAnalysisService = spendAnalysisService;
        this.recommendationService = recommendationService;
    }

    public Optional<WeeklySpendAnalysis> composeFor(Long userId) {
        SpendFacts facts = spendAnalysisService.factsFor(userId);
        if (facts.isEmpty()) {
            return Optional.empty();
        }

        List<String> recommendations = recommendationService.getRecommendations(userId).recommendations();
        return Optional.of(new WeeklySpendAnalysis(
                "Your FinMe spend summary for " + facts.month(),
                body(facts, recommendations)));
    }

    private static String body(SpendFacts facts, List<String> recommendations) {
        StringBuilder text = new StringBuilder();
        text.append("Here's how your spending looked in ").append(facts.month()).append(".\n\n");

        text.append("Total spent: ").append(money(facts.monthSpend())).append('\n');
        text.append("Previous month: ").append(money(facts.previousMonthSpend())).append('\n');
        text.append(changeSentence(facts)).append("\n\n");

        text.append("Where it went:\n");
        for (SpendFacts.CategoryFact category : facts.topCategories()) {
            text.append(String.format(Locale.ROOT, "  %-16s %12s   %s%n",
                    category.category(), money(category.amount()), categoryChange(category)));
        }

        if (!recommendations.isEmpty()) {
            text.append("\nWhat to look at:\n");
            recommendations.forEach(r -> text.append("  - ").append(r).append('\n'));
        }

        text.append("\nThese figures cover ").append(facts.transactionCount())
                .append(" transactions. Income and refunds are excluded from spending totals.\n");
        text.append("\nOpen FinMe to see the full breakdown.\n");
        return text.toString();
    }

    private static String changeSentence(SpendFacts facts) {
        int direction = facts.changeAmount().signum();
        if (direction == 0) {
            return "Exactly level with last month.";
        }

        String amount = money(facts.changeAmount().abs());
        String percent = facts.changePercent() == null
                ? ""
                : String.format(Locale.ROOT, " (%.1f%%)", facts.changePercent().abs());
        return direction > 0
                ? "That's " + amount + percent + " more than last month."
                : "That's " + amount + percent + " less than last month.";
    }

    private static String categoryChange(SpendFacts.CategoryFact category) {
        int direction = category.changeAmount().signum();
        if (direction == 0) {
            return "unchanged";
        }
        String amount = money(category.changeAmount().abs());
        return direction > 0 ? "up " + amount : "down " + amount;
    }

    private static String money(BigDecimal amount) {
        return String.format(Locale.ROOT, "R%,.2f", amount);
    }

    public record WeeklySpendAnalysis(String subject, String body) {
    }
}
