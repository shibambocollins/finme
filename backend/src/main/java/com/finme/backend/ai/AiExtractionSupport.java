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
                + "\"amount\": number, \"direction\": \"DEBIT or CREDIT\", "
                + "\"category\": \"string\", \"description\": \"string\"}]}\n"
                + "Always report \"amount\" as a POSITIVE number, and use \"direction\" to say "
                + "which way the money moved: \"DEBIT\" for money leaving the account "
                + "(purchases, fees, debit orders - the Debit column) and \"CREDIT\" for money "
                + "arriving (salaries, deposits, refunds, reversals - the Credit column). The "
                + "column the figure sits under decides this, not the merchant name. A refund "
                + "keeps the category of whatever was originally bought - a returned grocery "
                + "item is still \"Groceries\" - and is marked CREDIT.\n"
                + "Categories should be one of: Groceries, Transport, Entertainment, Utilities, "
                + "Dining, Shopping, Health, Income, Other. If no transactions are found, "
                + "return {\"transactions\": []}.\n\n"
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
                + "\"amount\": number, \"direction\": \"DEBIT or CREDIT\", "
                + "\"category\": \"string\", \"description\": \"string\", "
                + "\"paymentMethod\": \"CASH or CARD or UNKNOWN\"}]}\n"
                + "Report \"amount\" as a POSITIVE number. \"direction\" is \"DEBIT\" for an "
                + "ordinary purchase receipt; use \"CREDIT\" only when the slip is explicitly a "
                + "refund, return, or credit note.\n"
                + "Categories should be one of: Groceries, Transport, Entertainment, Utilities, "
                + "Dining, Shopping, Health, Income, Other. Use today's date if no date is "
                + "visible on the receipt. If paymentMethod isn't shown or determinable, use "
                + "\"UNKNOWN\". If this image is not a receipt, return "
                + "{\"transactions\": []}.";
    }

    /**
     * Asks for spend recommendations over figures that have already been calculated (FR-1.7.4).
     * <p>
     * The instruction not to compute or estimate anything is the important line. Everything
     * numeric in the summary was produced by deterministic code (see SpendMath), and FR-2.2.1
     * requires it stays that way - a model that helpfully works out its own percentage would
     * put a figure on the dashboard that the application cannot stand behind. Asking it to
     * quote only what it was given makes any invented number an obvious defect rather than an
     * indistinguishable one.
     */
    static String buildRecommendationPrompt(String spendFactsSummary) {
        return "You are a personal finance assistant. Below is a summary of one user's "
                + "spending, already calculated. Write 3 short, specific, actionable "
                + "recommendations based on it.\n"
                + "Rules:\n"
                + "- Do NOT calculate, estimate, or infer any number. Quote only figures that "
                + "appear verbatim in the summary below.\n"
                + "- Prioritise the largest changes and the largest categories.\n"
                + "- One sentence each, plain language, addressed to the user as \"you\".\n"
                + "- No greetings, no preamble, no markdown.\n"
                + "Respond with ONLY a JSON object of this exact shape:\n"
                + "{\"recommendations\": [\"string\", \"string\", \"string\"]}\n\n"
                + "Spending summary:\n" + spendFactsSummary;
    }

    /**
     * Reads the recommendations array, keeping only non-blank strings. A model that returns
     * fewer than asked, or pads with empty entries, yields a shorter list rather than an error -
     * an imperfect set of suggestions is still useful, and this is advisory text, not a figure
     * anyone will act on financially.
     */
    static List<String> parseRecommendations(String jsonContent) {
        JsonNode root;
        try {
            root = MAPPER.readTree(jsonContent);
        } catch (Exception ex) {
            throw new AiProviderException("Could not parse AI recommendations as JSON", ex);
        }

        JsonNode recommendations = root.get("recommendations");
        if (recommendations == null || !recommendations.isArray()) {
            throw new AiProviderException("AI response JSON had no 'recommendations' array");
        }

        List<String> result = new ArrayList<>();
        for (JsonNode node : recommendations) {
            if (node.isTextual() && !node.asText().isBlank()) {
                result.add(node.asText().trim());
            }
        }
        return result;
    }

    /**
     * Pulls choices[0].message.content out of an OpenAI-compatible chat completion response,
     * rejecting a response the model didn't finish.
     * <p>
     * The finish_reason check is the important part. A truncated extraction does NOT reliably
     * produce broken JSON that parsing would catch - verified live against Groq on 2026-08-21,
     * where hitting the token cap yielded a perfectly valid {"transactions":[...]} holding 1 of
     * 16 real transactions. Silently trusting that is worse than failing: the dashboard reports
     * a confident, wrong total with nothing logged. Treating it as a provider failure lets
     * FallbackAiProviderChain try the next provider, whose limits differ.
     */
    static String extractOpenAiMessageContent(String responseBody, String providerName) {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode finishReason = root.at("/choices/0/finish_reason");
            if ("length".equals(finishReason.asText())) {
                throw new AiProviderException(providerName
                        + " hit its output token limit mid-extraction (finish_reason=length) - the"
                        + " transaction list would be silently incomplete");
            }
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
            String direction = optionalText(node, "direction");
            return new ExtractedTransaction(
                    date, merchant, amount, category, description, paymentMethod, direction);
        } catch (DateTimeParseException | NumberFormatException | ArithmeticException | NullPointerException ex) {
            // Skip a malformed entry rather than guess at bad financial data - the rest of
            // the batch is still usable.
            return null;
        }
    }

    /** null, JSON null, and "" all mean "not extracted" - callers only ever branch on null. */
    private static String optionalText(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value.isBlank() ? null : value;
    }
}
