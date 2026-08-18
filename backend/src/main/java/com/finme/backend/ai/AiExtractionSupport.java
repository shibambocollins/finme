package com.finme.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared prompt/parsing logic for every AiProvider implementation - not part of the public
 * AiProvider contract, just an internal helper so Groq/OpenRouter/Cloudflare don't each
 * duplicate this.
 */
final class AiExtractionSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiExtractionSupport() {
    }

    static String buildStatementPrompt(String redactedText) {
        return "You are a financial transaction extraction assistant. Given raw bank statement "
                + "text, extract every distinct transaction you can find. Respond with ONLY a "
                + "JSON object of this exact shape, and nothing else - no markdown, no "
                + "commentary:\n"
                + "{\"transactions\": [{\"date\": \"YYYY-MM-DD\", \"merchant\": \"string\", "
                + "\"amount\": number, \"category\": \"string\", \"description\": \"string\"}]}\n"
                + "Categories should be one of: Groceries, Transport, Entertainment, Utilities, "
                + "Dining, Shopping, Health, Income, Other. If no transactions are found, return "
                + "{\"transactions\": []}.\n\n"
                + "Statement text:\n" + redactedText;
    }

    /**
     * A receipt photo is one purchase, not a list to scan through - asks for the receipt's
     * total (not itemized line items, which would over-fragment compared to how a statement
     * represents the same purchase as one line) plus paymentMethod, which a statement extraction
     * never needs (always CARD, set by the caller) but a receipt may show printed.
     */
    static String buildReceiptPrompt() {
        return "You are a financial transaction extraction assistant. This image is a photo of "
                + "a single purchase receipt. Extract ONE transaction representing the receipt's "
                + "total (not each line item). Respond with ONLY a JSON object of this exact "
                + "shape, and nothing else - no markdown, no commentary:\n"
                + "{\"transactions\": [{\"date\": \"YYYY-MM-DD\", \"merchant\": \"string\", "
                + "\"amount\": number, \"category\": \"string\", \"description\": \"string\", "
                + "\"paymentMethod\": \"CASH or CARD or UNKNOWN\"}]}\n"
                + "Categories should be one of: Groceries, Transport, Entertainment, Utilities, "
                + "Dining, Shopping, Health, Income, Other. Use today's date if no date is "
                + "visible on the receipt. If paymentMethod isn't shown or determinable, use "
                + "\"UNKNOWN\". If this image is not a receipt, return {\"transactions\": []}.";
    }

    /** Pulls choices[0].message.content out of an OpenAI-compatible chat completion response. */
    static String extractOpenAiMessageContent(String responseBody, String providerName) {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode content = root.at("/choices/0/message/content");
            if (content.isMissingNode()) {
                throw new AiProviderException(providerName + " response had no message content");
            }
            return content.asText();
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiProviderException(providerName + " response was not valid JSON", ex);
        }
    }

    /**
     * Pulls the model's output out of a Cloudflare Workers AI response. Verified empirically
     * against the real API (2026-08-18, both the chat-completion text path and the vision
     * path) - Cloudflare's own docs examples for this were confirmed broken via a flagged
     * GitHub issue, so this isn't a guess. Real shape: "result.response" nested, and - the
     * part the docs don't mention at all - already a parsed JSON *object* matching our
     * requested schema, not a string to re-parse. Still falls back to "result" as a plain
     * string / "result.response" as a string, in case a different model/endpoint on
     * Cloudflare's side ever returns one of those instead.
     */
    static String extractCloudflareResult(String responseBody) {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode result = root.get("result");
            if (result == null || result.isMissingNode()) {
                throw new AiProviderException("Cloudflare response had no 'result' field");
            }
            if (result.isTextual()) {
                return result.asText();
            }
            JsonNode nestedResponse = result.get("response");
            if (nestedResponse != null && nestedResponse.isTextual()) {
                return nestedResponse.asText();
            }
            if (nestedResponse != null && nestedResponse.isObject()) {
                return nestedResponse.toString();
            }
            throw new AiProviderException("Cloudflare 'result' was neither a string nor had a 'response' field");
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiProviderException("Cloudflare response was not valid JSON", ex);
        }
    }

    /**
     * Pulls the first balanced-looking {...} substring out of text that might contain
     * surrounding prose or markdown fencing - for providers without a guaranteed JSON mode.
     */
    static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new AiProviderException("Response did not contain a JSON object");
        }
        return text.substring(start, end + 1);
    }

    static List<ExtractedTransaction> parseTransactions(String jsonContent) {
        JsonNode root;
        try {
            root = MAPPER.readTree(jsonContent);
        } catch (Exception ex) {
            throw new AiProviderException("Could not parse AI response as JSON", ex);
        }

        JsonNode transactions = root.get("transactions");
        if (transactions == null || !transactions.isArray()) {
            throw new AiProviderException("AI response JSON had no 'transactions' array");
        }

        List<ExtractedTransaction> result = new ArrayList<>();
        for (JsonNode node : transactions) {
            ExtractedTransaction transaction = toTransaction(node);
            if (transaction != null) {
                result.add(transaction);
            }
        }
        return result;
    }

    private static ExtractedTransaction toTransaction(JsonNode node) {
        try {
            LocalDate date = LocalDate.parse(node.get("date").asText());
            String merchant = node.get("merchant").asText();
            // decimalValue(), not asText()+new BigDecimal(String) - avoids routing a monetary
            // amount through a JSON-number's double representation, which can lose precision.
            JsonNode amountNode = node.get("amount");
            BigDecimal amount = amountNode.isTextual()
                    ? new BigDecimal(amountNode.asText())
                    : amountNode.decimalValue();
            String category = node.has("category") ? node.get("category").asText() : null;
            String description = node.has("description") ? node.get("description").asText() : null;
            String paymentMethod = node.has("paymentMethod") ? node.get("paymentMethod").asText() : null;
            return new ExtractedTransaction(date, merchant, amount, category, description, paymentMethod);
        } catch (DateTimeParseException | NumberFormatException | ArithmeticException | NullPointerException ex) {
            // Skip a malformed entry rather than guess at bad financial data - the rest of
            // the batch is still usable.
            return null;
        }
    }
}
