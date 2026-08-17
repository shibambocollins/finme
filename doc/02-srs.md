# FinMe — Software Requirements Specification

Scope and objectives per `01-project-proposal.md`. Requirements are numbered and written as
testable "shall" statements, grouped by module.

## 1. Phase 1 Functional Requirements

### 1.1 Authentication & Authorization

- FR-1.1.1 — The system shall require user registration and login before any financial data
  can be accessed.
- FR-1.1.2 — The system shall enforce that a user can only read or write their own data, at
  the data-access layer, not only at the UI layer.
- FR-1.1.3 — The system shall securely hash and store user credentials; passwords shall never
  be logged or transmitted to any third-party service, including AI providers.

### 1.2 Statement & Receipt Ingestion

- FR-1.2.1 — The system shall allow a user to upload one or more bank statement PDF files.
- FR-1.2.2 — The system shall extract text from uploaded PDF statements locally using Apache
  PDFBox, without sending the raw PDF file to any external AI provider.
- FR-1.2.3 — The system shall allow a user to upload one or more receipt/invoice images.
- FR-1.2.4 — The system shall extract transaction data from receipt images using a
  vision-capable LLM, since receipt images have no extractable text layer.
- FR-1.2.5 — The system shall track the source of every transaction (bank statement, receipt,
  or manual entry) and the payment method (cash or card) where determinable.

### 1.3 Data Privacy & Redaction

- FR-1.3.1 — The system shall select only transaction-relevant fields (date, merchant,
  amount, description) from extracted statement text before constructing any AI prompt.
- FR-1.3.2 — The system shall apply a redaction pass removing account numbers, ID numbers,
  and personal names from any text before it is sent to an external AI provider, as a
  safety-net layer independent of field-level extraction.
- FR-1.3.3 — The system shall never enable prompt-logging or data-retention options on any AI
  provider where an opt-out exists, and shall never route requests through a free-tier AI
  endpoint that permits training use on submitted content.

### 1.4 AI Extraction & Categorization

- FR-1.4.1 — The system shall convert extracted, redacted transaction text into structured
  transaction records (date, merchant, amount, category) via an LLM call.
- FR-1.4.2 — The system shall fall back to an alternate AI provider if the primary provider
  is unavailable or errors, per the AI fallback chain defined in the design document.
- FR-1.4.3 — The system shall assign a spend category (e.g., food, transport) to each
  extracted transaction.

### 1.5 Manual & Cash Transaction Entry

- FR-1.5.1 — The system shall allow a user to log a transaction via a natural-language prompt
  (e.g., "I bought lunch for R150 today, paid cash").
- FR-1.5.2 — The system shall parse the prompt into a structured transaction record with the
  same schema as statement- and receipt-sourced transactions.

### 1.6 Duplicate Transaction Handling

- FR-1.6.1 — The system shall attempt to match a newly ingested statement transaction against
  existing card-payment receipt-sourced transactions for the same user within a configurable
  date window, based on exact amount match.
- FR-1.6.2 — The system shall never attempt to match a cash-payment transaction against a
  bank statement, since cash transactions do not appear on statements.
- FR-1.6.3 — When a match is found, the system shall mark the receipt-sourced transaction as
  superseded (not deleted) and treat the statement-sourced transaction as authoritative.

### 1.7 Dashboard & Visualization

- FR-1.7.1 — The system shall display total spend, category breakdown, and time-based trend
  charts.
- FR-1.7.2 — The system shall display a map-based visualization of spend by location where
  location data is available.
- FR-1.7.3 — The system shall display a recent-activity feed of the latest transactions.
- FR-1.7.4 — The system shall display AI-generated spend recommendations alongside the
  dashboard.

### 1.8 Automated Insights & Notifications

- FR-1.8.1 — The system shall generate a weekly spend analysis summarizing spending patterns
  and trend changes.
- FR-1.8.2 — The system shall deliver the weekly analysis via scheduled email.

### 1.9 Evaluation Harness

- FR-1.9.1 — The system's development process shall maintain a labeled golden test set of
  real (or representative) bank statements with manually verified expected transactions.
- FR-1.9.2 — The system's development process shall maintain a separate labeled golden test
  set of receipt images.
- FR-1.9.3 — The evaluation harness shall report transaction-detection precision and recall,
  category accuracy, and hallucination rate, scored separately for the PDF pipeline and the
  photo pipeline.

## 2. Phase 2 Functional Requirements — Credit Score Module

### 2.1 Credit Profile Management

- FR-2.1.1 — The system shall allow a user to optionally create a credit profile linked to
  their existing account (no separate login or identity system required).
- FR-2.1.2 — The system shall allow manual entry of credit accounts, including balance,
  credit limit, and payment history.
- FR-2.1.3 — The system shall allow manual entry and periodic update of the user's credit
  score.
- FR-2.1.4 — The credit profile shall store a bureau identifier and a maximum score value
  (default: Experian, 740), not a hardcoded scale, so other bureaus can be supported without
  a schema change.

### 2.2 Credit Calculation Engine

- FR-2.2.1 — The system shall calculate credit utilization (balance divided by limit) using
  deterministic arithmetic, not an AI-generated estimate.
- FR-2.2.2 — The system shall identify which credit accounts are contributing most negatively
  to overall utilization.

### 2.3 AI Credit Analysis & Recommendations

- FR-2.3.1 — The system shall generate a narrative analysis and prioritized improvement plan
  based on the deterministically calculated credit position.
- FR-2.3.2 — The system shall provide a "what-if" simulation showing the effect of a
  hypothetical balance change on calculated utilization.
- FR-2.3.3 — The system shall display a clearly visible disclaimer stating that
  recommendations are not guaranteed to increase the user's credit score.

### 2.4 Progress Tracking

- FR-2.4.1 — The system shall store a dated snapshot each time a user updates their credit
  information.
- FR-2.4.2 — The system shall allow comparison between the current snapshot and any previous
  snapshot.

## 3. Non-Functional Requirements

- NFR-1 (Security) — All financial data shall be isolated per user at the data-access layer;
  no query shall be able to return another user's records.
- NFR-2 (Privacy) — No raw account number, ID number, or personal name shall be transmitted
  to an external AI provider under any code path.
- NFR-3 (Data Retention) — All external AI provider integrations shall be configured for
  zero or minimum data retention where the provider offers that option, and this
  configuration shall be re-verified periodically, since provider policies can change.
- NFR-4 (Provider Exclusion) — Gemini shall never be called on any code path that processes
  real user financial data (transactions, balances, credit information, categorization,
  recommendations), regardless of tier. Gemini's use is limited to a separate, isolated
  code path for generic, data-free content generation only. The production AI fallback chain
  for real data is Groq, OpenRouter, and Cloudflare Workers AI.
- NFR-5 (Compliance) — Handling of personal financial data shall have regard to POPIA
  (Protection of Personal Information Act) principles, given the personal and financial
  nature of the data processed.
- NFR-6 (Extensibility) — The credit score data model shall not hardcode a single bureau's
  scale as a global constant.
- NFR-7 (Reliability) — AI extraction shall fall back to an alternate provider on failure
  rather than surfacing an unhandled error to the user.
- NFR-8 (Auditability) — Superseded or duplicate-flagged transactions shall be retained, not
  deleted, to preserve an audit trail for debugging extraction and matching logic.
- NFR-9 (Credential Isolation) — Each AI provider integration shall use a dedicated API key
  for this project, not a key shared with any other application, to limit blast radius and
  allow independent revocation.

## 4. Explicitly Out of Scope

Restated from the proposal for completeness: Business Finance module, family/shared account
access, automated bank account integration (Open Banking), multi-currency support.
