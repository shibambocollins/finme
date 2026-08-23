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

**Implementation (built 2026-08-23).** `ExtractionEvaluationHarnessTest`, run on demand:

```bash
cd backend
./mvnw test -Dtest=ExtractionEvaluationHarnessTest -Dlive.ai=true
```

Gated behind `-Dlive.ai=true` because it spends real API quota; a normal `./mvnw test` skips
it. The report prints and is written to `target/extraction-evaluation.txt` so runs can be
compared over time.

- A reported transaction matches a labeled one on **date and amount**. Merchant text is
  excluded from matching — models paraphrase it, and scoring on string equality would report a
  naming difference as both a miss and an invention.
- Pairing is one-to-one, so a transaction reported twice scores one match and one invention.
  This is what catches duplicate-inflation bugs, which otherwise look like perfect recall.
- Category accuracy is scored over matched transactions only, so a detection failure is
  counted once (against recall) rather than twice.
- A document the chain cannot process at all counts as a total miss, never as a skip —
  dropping the hardest cases would quietly raise the average.
- The harness's own arithmetic is unit-tested in `ExtractionEvaluatorTest`. A harness whose
  scoring is itself unverified would report confident numbers about accuracy while being wrong,
  which is the exact habit it exists to break.

Real labeled documents live in `backend/src/test/resources/golden/` and are gitignored — they
contain real financial data. When that directory is empty the harness falls back to a generated
synthetic set and labels the report `SYNTHETIC`. Synthetic documents are clean (no scan skew,
no column drift, no faded thermal print), so those scores are a **floor on difficulty, not a
sample of it**. See that directory's README for the label format.

**First measured run (2026-08-23, synthetic set, Groq primary):**

| | PDF (statements) | Photo (receipts) |
|---|---|---|
| documents | 4 | 3 |
| precision | 1.000 | 1.000 |
| recall | 1.000 | 1.000 |
| F1 | 1.000 | 1.000 |
| category accuracy | 0.851 | 1.000 |
| hallucination rate | 0.000 | 0.000 |

Detection is not the weak point on clean input; **categorisation is**. The failures were
systematic rather than random — Woolworths and Checkers read as "Shopping" instead of
"Groceries", Shell as "Utilities" instead of "Transport" — the signature of undefined category
boundaries rather than a weak model.

**Second run (same day), after replacing the bare category list with definitions:**

| | before | after |
|---|---|---|
| category accuracy (PDF) | 0.851 | **0.957** |
| precision / recall / hallucination | 1.000 / 1.000 / 0.000 | unchanged |

The definitions were written as principles ("the merchant's primary business decides"), not as
a list of the merchants that failed. Naming those would have raised this score while teaching
the model nothing about the next statement — improving the measurement instead of the thing
measured.

The two remaining errors need *merchant knowledge* rather than clearer boundaries (Nandos read
as Shopping; a Gautrain card recharge as Other), so prompt work stops here. Note also that 47
transactions is a thin sample: a 10-point move is roughly five transactions. Treat it as a
direction, not a precise figure, until real labeled documents are in the golden set.

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
