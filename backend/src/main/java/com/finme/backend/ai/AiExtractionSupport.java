package com.finme.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

final class AiExtractionSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiExtractionSupport() {
    }

    private static final String COMMON_SOUTH_AFRICAN_MERCHANTS =
            "Common South African merchants, by category:\n"
                    + "- Groceries: Woolworths, Checkers, Checkers Hyper, Pick n Pay, Shoprite, "
                    + "Spar, Food Lover's Market, Makro.\n"
                    + "- Dining: Nandos, Steers, KFC, McDonald's, Wimpy, Debonairs, Roman's "
                    + "Pizza, Ocean Basket, Spur, Mugg & Bean, Vida e Caffe, Famous Brands.\n"
                    + "- Transport: Shell, Engen, Sasol, BP, Total, Caltex, Astron Energy, Uber, "
                    + "Bolt, Gautrain, SANRAL, e-toll.\n"
                    + "- Utilities: Eskom, City of Johannesburg, City of Cape Town, City of "
                    + "Tshwane, eThekwini, Vodacom, MTN, Telkom, Cell C, Rain, Afrihost.\n"
                    + "- Health: Clicks, Dis-Chem, Discovery Health, Momentum Health, Medshield, "
                    + "Bonitas, Netcare, Mediclinic.\n"
                    + "- Entertainment: Netflix, Showmax, DStv, MultiChoice, Spotify, "
                    + "Ster-Kinekor, Nu Metro.\n"
                    + "- Shopping: Takealot, Mr Price, Truworths, Foschini, Edgars, Game, "
                    + "Incredible Connection, Cotton On, Superbalist, Builders Warehouse.\n"
                    + "- Other: bank charges from FNB, Absa, Standard Bank, Nedbank, Capitec, "
                    + "TymeBank or Discovery Bank.\n"
                    + "A merchant not listed here is categorised by the definitions above.\n";

    private static final String CATEGORY_GUIDE =
            "Assign exactly one category from this list, using these definitions:\n"
                    + "- Groceries: supermarkets and food shops. A supermarket stays Groceries "
                    + "even when it also sells clothing or homeware.\n"
                    + "- Dining: restaurants, takeaways, fast food, cafes, bars.\n"
                    + "- Transport: fuel and petrol stations, ride-hailing, taxis, public "
                    + "transport, tolls, parking, vehicle servicing. A fuel station is Transport "
                    + "even though it also sells food.\n"
                    + "- Utilities: electricity, water, municipal accounts, internet, mobile "
                    + "airtime and data.\n"
                    + "- Health: pharmacies, doctors, hospitals, medical aid and health "
                    + "insurance premiums.\n"
                    + "- Entertainment: streaming services, cinema, events, games.\n"
                    + "- Shopping: clothing, electronics, furniture and general retail whose "
                    + "primary business is not food.\n"
                    + "- Income: money coming in - salary, deposits, interest received.\n"
                    + "- Other: bank charges and account fees, transfers, and anything that does "
                    + "not clearly fit a category above. Bank fees are Other, not Utilities.\n"
                    + "When a merchant could fit more than one, the merchant's primary business "
                    + "decides.\n"
                    + COMMON_SOUTH_AFRICAN_MERCHANTS;


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
                + CATEGORY_GUIDE
                + "If no transactions are found, return {\"transactions\": []}.\n\n"
                + "Statement text:\n" + redactedText;
    }

    static String buildManualEntryPrompt(String naturalLanguage, java.time.LocalDate today) {
        return "You are a financial transaction extraction assistant. Convert the user's "
                + "description of what they spent into structured transactions. Respond with "
                + "ONLY a JSON object of this exact shape, and nothing else - no markdown, no "
                + "commentary:\n"
                + "{\"transactions\": [{\"date\": \"YYYY-MM-DD\", \"merchant\": \"string\", "
                + "\"amount\": number, \"direction\": \"DEBIT or CREDIT\", "
                + "\"category\": \"string\", \"description\": \"string\", "
                + "\"paymentMethod\": \"CASH or CARD or UNKNOWN\"}]}\n"
                + "Today's date is " + today + ". Resolve any relative date - \"today\", "
                + "\"yesterday\", \"last Friday\" - against that date, and never invent a date "
                + "outside it. If the user gives no date at all, use today's date.\n"
                + "Report \"amount\" as a POSITIVE number. Use \"direction\" DEBIT for money "
                + "spent and CREDIT only if the user describes money received.\n"
                + "Set \"paymentMethod\" to CASH unless the user says otherwise; use CARD when "
                + "they mention a card, and UNKNOWN only if they explicitly say they do not "
                + "know.\n"
                + "If the user names no merchant, use a short description of the purchase as the "
                + "merchant. If the text describes no purchase at all, return "
                + "{\"transactions\": []}.\n"
                + CATEGORY_GUIDE
                + "\nUser description:\n" + naturalLanguage;
    }

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
                + CATEGORY_GUIDE
                + "Use today's date if no date is visible on the receipt. If paymentMethod isn't shown or determinable, use "
                + "\"UNKNOWN\". If this image is not a receipt, return "
                + "{\"transactions\": []}.";
    }

    /**
     * The model must not compute (utilization comes from CreditUtilization and is the whole
     * basis of the advice), and must not promise a score outcome - nobody can guarantee what a
     * bureau will do. The user-facing disclaimer (FR-2.3.3) is a constant added by
     * CreditAnalysisService rather than requested here, so no model output can weaken, reword or
     * omit it.
     */
    static String buildCreditAnalysisPrompt(String creditFactsSummary) {
        return "You are a credit coach. Below is a user's credit position, already calculated. "
                + "Write 3 short, prioritised, actionable steps to improve it.\n"
                + "Rules:\n"
                + "- Do NOT calculate, estimate, or infer any number. Quote only figures that "
                + "appear verbatim in the summary below.\n"
                + "- Do NOT promise or predict a credit score increase, a number of points, or a "
                + "timeframe. No one can guarantee how a bureau will respond.\n"
                + "- Order the steps by the \"lower overall utilization by\" figure, largest "
                + "first. That figure, not how close an account is to its own limit, is what "
                + "\"highest impact\" means here.\n"
                + "- One sentence each, plain language, addressed to the user as \"you\".\n"
                + "- No greetings, no preamble, no markdown.\n"
                + "Respond with ONLY a JSON object of this exact shape:\n"
                + "{\"recommendations\": [\"string\", \"string\", \"string\"]}\n\n"
                + "Credit position:\n" + creditFactsSummary;
    }

    /**
     * Everything numeric in the summary was produced by deterministic code (see SpendMath), and
     * FR-2.2.1 requires it stays that way - the model is only ever asked to quote what it was
     * given, never to compute.
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
     * The finish_reason check is the important part: a truncated extraction does not reliably
     * produce broken JSON that parsing alone would catch - it can yield a perfectly valid,
     * silently incomplete transaction list. Treating that as a provider failure (rather than
     * trusting it) lets FallbackAiProviderChain try the next provider instead of quietly
     * reporting a wrong total.
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
     * Cloudflare's own docs examples for this shape were confirmed wrong (a flagged upstream
     * GitHub issue, not a guess): the real response nests the result under "result.response",
     * already a parsed JSON <em>object</em> matching the requested schema, not a string to
     * re-parse. Still falls back to "result" or "result.response" as a plain string, in case a
     * different model or endpoint on Cloudflare's side ever returns one of those instead.
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
            return null;
        }
    }

    private static String optionalText(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value.isBlank() ? null : value;
    }
}
