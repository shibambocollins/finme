package com.finme.backend.ai;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiExtractionSupportTest {

    @Test
    void parsesAWellFormedTransactionsObject() {
        String json = """
                {"transactions": [
                  {"date": "2026-01-12", "merchant": "Woolworths", "amount": 450.00, "category": "Groceries", "description": "desc"}
                ]}
                """;

        List<ExtractedTransaction> result = AiExtractionSupport.parseTransactions(json);

        assertThat(result).containsExactly(
                new ExtractedTransaction(LocalDate.of(2026, 1, 12), "Woolworths",
                        new BigDecimal("450.00"), "Groceries", "desc"));
    }

    @Test
    void skipsAMalformedEntryButKeepsTheRest() {
        String json = """
                {"transactions": [
                  {"date": "not-a-date", "merchant": "Bad", "amount": 1, "category": "Other", "description": ""},
                  {"date": "2026-01-14", "merchant": "Uber", "amount": 85.50, "category": "Transport", "description": ""}
                ]}
                """;

        List<ExtractedTransaction> result = AiExtractionSupport.parseTransactions(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).merchant()).isEqualTo("Uber");
    }

    @Test
    void extractsOpenAiMessageContent() {
        String response = """
                {"choices": [{"message": {"content": "{\\"transactions\\": []}"}}]}
                """;

        String content = AiExtractionSupport.extractOpenAiMessageContent(response, "Groq");

        assertThat(content).isEqualTo("{\"transactions\": []}");
    }

    @Test
    void extractsCloudflareResult() {
        String response = """
                {"result": {"response": "{\\"transactions\\": []}"}, "success": true}
                """;

        String content = AiExtractionSupport.extractCloudflareResult(response);

        assertThat(content).isEqualTo("{\"transactions\": []}");
    }

    @Test
    void extractsJsonObjectFromSurroundingProse() {
        String messy = "Sure, here you go:\n```json\n{\"transactions\": []}\n```\nHope that helps!";

        String result = AiExtractionSupport.extractJsonObject(messy);

        assertThat(result).isEqualTo("{\"transactions\": []}");
    }

    @Test
    void throwsWhenNoJsonObjectIsPresent() {
        assertThatThrownBy(() -> AiExtractionSupport.extractJsonObject("no json here"))
                .isInstanceOf(AiProviderException.class);
    }
}
