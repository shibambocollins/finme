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

    /**
     * The merchants that actually dominate South African bank statements, named explicitly.
     * <p>
     * The definitions above draw the boundaries; this supplies the knowledge to place a merchant
     * inside them. They solve different failures. A model can understand perfectly that
     * restaurants are Dining and still not know that "NANDOS" is a restaurant - which is exactly
     * what the harness caught, along with a Gautrain card recharge landing in Other.
     * <p>
     * This is domain knowledge, not tuning to a test set: the app is ZAR-only and
     * single-country by design (see 01-project-proposal.md), so the universe of merchants a real
     * statement contains is small, stable, and knowable. The list is drawn from the major
     * national chains, not from the rows that happened to fail in the golden set.
     * <p>
     * One consequence to be honest about: several of these names do appear in the golden set, so
     * the harness's category-accuracy figure is no longer fully independent of this prompt for
     * those merchants. The definitions and the unlisted-merchant rule are what still generalise,
     * and a real labeled statement is what will genuinely test this.
     */
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

    /**
     * Category definitions shared by both prompts.
     * <p>
     * The bare list these replace ("Categories should be one of: ...") left every boundary to
     * the model's guess, and the evaluation harness showed the cost: category accuracy 0.851 on
     * the PDF pipeline while detection scored a clean 1.000. The errors were systematic, not
     * random - supermarkets read as "Shopping", fuel stations as "Utilities", bank fees as
     * "Utilities" - which is the signature of undefined boundaries rather than a weak model.
     * <p>
     * These are deliberately written as <em>principles</em> ("the merchant's primary business
     * decides"), not as a list of merchant names. Naming the specific merchants that failed
     * would raise the score on this golden set while teaching the model nothing about the next
     * statement - the classic way to make an evaluation number improve without improving the
     * thing it measures.
     */
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

    /**
     * A receipt photo is one purchase, not a list to scan through - asks for the receipt's
     * total (not itemized line items, which would over-fragment compared to how a statement
     * represents the same purchase as one line) plus paymentMethod, which a statement extraction
     * never needs (always CARD, set by the caller) but a receipt may show printed.
     */
    /**
     * Parses a free-text description of a purchase into transactions (FR-1.5.1, FR-1.5.2).
     * <p>
     * Today's date is supplied rather than left to the model. Users write "yesterday" and "last
     * Friday", and a model has no reliable clock - asking it to resolve a relative date against
     * a date it guessed is how a transaction silently lands in the wrong month, and therefore
     * the wrong figure on the dashboard. The application knows the date; it states it.
     * <p>
     * Payment method defaults to CASH because that is what this feature exists for: purchases
     * that will never appear on a statement or produce a receipt. An explicit mention of a card
     * still wins - the default applies to silence, not to contradiction.
     */
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
     * Asks for spend recommendations over figures that have already been calculated (FR-1.7.4).
     * <p>
     * The instruction not to compute or estimate anything is the important line. Everything
     * numeric in the summary was produced by deterministic code (see SpendMath), and FR-2.2.1
     * requires it stays that way - a model that helpfully works out its own percentage would
     * put a figure on the dashboard that the application cannot stand behind. Asking it to
     * quote only what it was given makes any invented number an obvious defect rather than an
     * indistinguishable one.
     */
    /**
     * Asks for a prioritised credit improvement plan over already-calculated figures
     * (FR-2.3.1).
     * <p>
     * Two constraints matter more here than in the spend equivalent. The model must not compute
     * - utilization comes from CreditUtilization and is the whole basis of the advice. And it
     * must not promise a score outcome: nobody can guarantee what a bureau will do, so a
     * sentence like "this will raise your score by 40 points" would be a fabrication the app
     * appears to stand behind. The user-facing disclaimer (FR-2.3.3) is a constant added by
     * CreditAnalysisService rather than requested here, so no model output can weaken, reword
     * or omit it.
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
