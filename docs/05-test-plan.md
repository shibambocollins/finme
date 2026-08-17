# FinMe — Test Plan

## 1. Testing Levels

- **Unit tests** — redaction logic, duplicate-matching logic, utilization calculation,
  category assignment rules.
- **Integration tests** — ingestion pipeline end-to-end (upload → extract → redact →
  categorize → persist) for both statement and receipt sources.
- **End-to-end tests** — full user flow from registration through statement upload to
  dashboard display.

## 2. Evaluation Harness Methodology

This is the project's central technical validation, distinguishing it from a typical CRUD
finance tracker: extraction accuracy is measured, not assumed.

**Golden set construction.** Two separate labeled sets:

- 15–20 real or representative bank statements, manually labeled with the exact expected
  transactions (date, merchant, amount, category).
- A separate set of photographed receipts/invoices, manually labeled the same way.

**Metrics, scored separately for each pipeline (PDF vs. photo), since accuracy is expected to
differ meaningfully between them:**

- Transaction-detection precision and recall (did the pipeline find the transactions that are
  actually present, without inventing ones that aren't).
- Category-assignment accuracy against the labeled category.
- Hallucination rate — transactions reported that do not exist in the source document.

**Why this matters beyond a nice-to-have metric:** this is money-tracking software. A
pipeline that is right 95% of the time versus 70% of the time is the difference between a
tool someone can trust and one that quietly misleads them. The number needs to be known, not
assumed.

## 3. Security & Privacy Testing

- Unit test asserting the Redaction Service strips known account-number and ID-number
  patterns from sample statement text.
- Unit test asserting no field beyond date/merchant/amount/description is present in the
  payload constructed for the AI Integration Layer.
- Integration test confirming the production AI Integration Layer (real-data code paths) can
  only reach Groq, OpenRouter, and Cloudflare Workers AI — no Gemini call path reachable from
  any endpoint that processes transactions, balances, credit data, or recommendations.
- Integration test confirming the isolated Gemini dev-only path cannot be invoked with any
  payload containing real user data (transaction amounts, merchant names, balances) — only
  generic, data-free prompts should type-check or compile against that path.

## 4. Duplicate Detection Test Cases

- Card-payment receipt transaction followed by a matching statement transaction within the
  date window → receipt transaction flagged superseded, statement transaction authoritative.
- Cash-payment receipt transaction → never checked against statement data, remains active
  regardless of any statement contents.
- Two genuinely distinct transactions with the same amount on the same day (edge case) →
  documented as an accepted limitation in the risk register, not silently mismatched without
  acknowledgment.

## 5. Acceptance Criteria (examples)

- A user can upload a real Capitec statement and see at least 90% of transactions correctly
  extracted and categorized, measured against the golden set.
- No account number, ID number, or name appears in any logged AI request payload during
  integration testing.
- A duplicate card transaction across receipt and statement upload results in exactly one
  active transaction record, not two.
