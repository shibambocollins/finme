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

        // BigDecimal via a JSON number carries no guaranteed scale ("450.00" and "450.0" are
        // the same JSON number) - compare by value (compareTo), not by strict equals().
        assertThat(result).hasSize(1);
        ExtractedTransaction transaction = result.get(0);
        assertThat(transaction.date()).isEqualTo(LocalDate.of(2026, 1, 12));
        assertThat(transaction.merchant()).isEqualTo("Woolworths");
        assertThat(transaction.amount()).isEqualByComparingTo("450.00");
        assertThat(transaction.category()).isEqualTo("Groceries");
        assertThat(transaction.description()).isEqualTo("desc");
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
    void extractsCloudflareResultWhenNested() {
        String response = """
                {"result": {"response": "{\\"transactions\\": []}"}, "success": true}
                """;

        String content = AiExtractionSupport.extractCloudflareResult(response);

        assertThat(content).isEqualTo("{\"transactions\": []}");
    }

    @Test
    void extractsCloudflareResultWhenResponseIsAlreadyAJsonObject() {
        // The real shape, confirmed against the live API on 2026-08-18 (both the chat-format
        // text call and the vision call): "result.response" isn't a string to re-parse at all,
        // it's already a parsed JSON object matching our schema. Regression coverage for a
        // real bug this exact case caused - extractCloudflareResult originally only checked
        // isTextual() here and threw on every real Cloudflare response.
        String response = """
                {"result": {"response": {"transactions": []}}, "success": true}
                """;

        String content = AiExtractionSupport.extractCloudflareResult(response);

        assertThat(content).isEqualTo("{\"transactions\":[]}");
    }

    @Test
    void extractsCloudflareResultWhenAPlainString() {
        // Cloudflare's own published examples disagree on this shape (confirmed via a flagged
        // docs GitHub issue) - defensive parsing must handle both.
        String response = """
                {"result": "{\\"transactions\\": []}", "success": true}
                """;

        String content = AiExtractionSupport.extractCloudflareResult(response);

        assertThat(content).isEqualTo("{\"transactions\": []}");
    }

    @Test
    void parsesOptionalPaymentMethodFromAReceiptTransaction() {
        String json = """
                {"transactions": [
                  {"date": "2026-01-12", "merchant": "Corner Cafe", "amount": 65.00, "category": "Dining", "description": "desc", "paymentMethod": "CASH"}
                ]}
                """;

        List<ExtractedTransaction> result = AiExtractionSupport.parseTransactions(json);

        assertThat(result.get(0).paymentMethod()).isEqualTo("CASH");
    }

    @Test
    void paymentMethodIsNullWhenAbsent() {
        List<ExtractedTransaction> result = AiExtractionSupport.parseTransactions(
                "{\"transactions\": [{\"date\": \"2026-01-12\", \"merchant\": \"m\", \"amount\": 1, \"category\": \"c\", \"description\": \"d\"}]}");

        assertThat(result.get(0).paymentMethod()).isNull();
    }

    @Test
    void parsesOptionalAddressFromAReceiptTransaction() {
        String json = """
                {"transactions": [
                  {"date": "2026-01-12", "merchant": "Woolworths", "amount": 120.00, "category": "Groceries", "description": "desc", "paymentMethod": "CARD", "address": "1 Sandton Dr, Sandton"}
                ]}
                """;

        List<ExtractedTransaction> result = AiExtractionSupport.parseTransactions(json);

        assertThat(result.get(0).address()).isEqualTo("1 Sandton Dr, Sandton");
    }

    @Test
    void addressIsNullWhenAbsentOrExplicitlyNullOrBlank() {
        String json = """
                {"transactions": [
                  {"date": "2026-01-12", "merchant": "a", "amount": 1, "category": "c", "description": "d"},
                  {"date": "2026-01-12", "merchant": "b", "amount": 1, "category": "c", "description": "d", "address": null},
                  {"date": "2026-01-12", "merchant": "c", "amount": 1, "category": "c", "description": "d", "address": "  "}
                ]}
                """;

        List<ExtractedTransaction> result = AiExtractionSupport.parseTransactions(json);

        assertThat(result).extracting(ExtractedTransaction::address).containsExactly(null, null, null);
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
