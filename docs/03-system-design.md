# FinMe — System Design Document

## 1. Architecture Overview

Layered monolith: React frontend deployed on Vercel, Spring Boot backend (Controller →
Service → Repository) deployed on Azure. Database is H2 for local development and testing,
MySQL on Azure for production, switched via Spring profiles rather than two different
codebases. One backend, one database, one authentication session serving both Phase 1
(personal tracker) and Phase 2 (credit module) — there is no service-to-service identity
federation between them, since they are features within the same application, not separate
systems. Authentication is JWT-based, issued by the backend after login via either email/
password or Google OAuth (Spring Security's OAuth2 client) — both paths converge on the same
token scheme, so the rest of the application only ever checks one thing regardless of how the
user signed in.

External AI calls (extraction, categorization, credit narrative generation) are isolated
behind a single AI Integration Layer, so provider swaps, fallback ordering, and
redaction/data-retention policy changes happen in one place rather than being scattered
through business logic.

## 2. Component Breakdown

- **Auth Service** — registration/login via email-password or Google OAuth, JWT issuance on
  successful authentication of either kind, per-user data scoping enforced at the repository
  layer.
- **Statement Ingestion Service** — accepts PDF upload, runs Apache PDFBox text extraction
  locally, hands extracted text to the Redaction Service before it reaches the AI layer.
- **Receipt Ingestion Service** — accepts image upload, sends the image to a vision-capable
  LLM via the AI Integration Layer for extraction (a separate pipeline from statement
  ingestion, since there is no local text-extraction step available for a photo).
- **Redaction Service** — field-level selection (keep only date/merchant/amount/description)
  plus a regex/heuristic pass removing account numbers, ID numbers, and personal names, run
  on all text before it leaves the Statement or Receipt Ingestion Service.
- **AI Integration Layer** — implements the production fallback chain (Groq → OpenRouter →
  Cloudflare Workers AI; exact ordering to be set based on real rate-limit and latency
  testing, not fixed here), and enforces provider-side zero-retention configuration. Gemini is
  intentionally excluded from this layer's real-data code paths — see Section 5.
- **Categorization Service** — takes structured transaction output from the AI layer and
  assigns a spend category.
- **Duplicate Detection Service** — on ingestion of a new statement transaction, checks
  card-payment receipt-sourced transactions for the same user within a date window for an
  exact amount match; on match, flags the receipt-sourced record `superseded` and keeps the
  statement record as authoritative. Never runs against cash-sourced transactions.
- **Dashboard/Analytics Service** — aggregates transactions for category totals, trend
  series, map coordinates (where available), and recent-activity feed.
- **Notification Service** — scheduled job (Spring `@Scheduled`) generating and emailing the
  weekly spend analysis.
- **Credit Module Service** — manages `CreditProfile`, `CreditAccount`, and `CreditSnapshot`
  entities; performs deterministic utilization calculation; calls the AI Integration Layer
  only for narrative analysis and the what-if simulation's explanatory text, never for the
  underlying math.
- **Evaluation Harness** — a development/test-time tool, not a runtime production service; 
  see `05-test-plan.md`.

## 3. Data Model

Entities and key fields, described in prose rather than as a formal ERD table:

- **User** — id, email, password_hash, created_at.
- **BankStatement** — id, user_id, upload_date, source_bank, status (processing / complete /
  failed).
- **Receipt** — id, user_id, upload_date, image_reference, status.
- **Transaction** — id, user_id, source_type (statement / receipt / manual), source_id
  (nullable, references the originating BankStatement or Receipt), date, merchant, amount,
  category, description, payment_method (cash / card / unknown), status (active /
  superseded), superseded_by (nullable, self-referencing).
- **CreditProfile** — id, user_id, bureau (default: Experian), max_score (default: 740),
  created_at.
- **CreditAccount** — id, credit_profile_id, account_name, balance, credit_limit,
  payment_status.
- **CreditSnapshot** — id, credit_profile_id, score, recorded_at — one row per update, enables
  previous-vs-current comparison.

## 4. Key Flows

**Statement Upload Flow.** User uploads PDF → PDFBox extracts raw text locally → Redaction
Service strips account/ID/name data and keeps only transaction-relevant fields → AI
Integration Layer structures and categorizes transactions → Duplicate Detection Service checks
card-payment transactions against existing receipt-sourced records → transactions persisted →
dashboard updated.

**Receipt Upload Flow.** User uploads image → AI Integration Layer (vision-capable model)
extracts transaction data directly from the image → same redaction, categorization, and
duplicate-check steps as above, with payment_method captured from the receipt where possible.

**Weekly Insight Flow.** Scheduled job aggregates the past week's transactions → AI
Integration Layer generates a narrative summary and suggestions → Notification Service emails
the result.

**Credit Snapshot Flow.** User manually enters or updates credit account/score data →
Credit Module Service calculates utilization deterministically → AI Integration Layer
generates narrative analysis and prioritized plan (with disclaimer) → new CreditSnapshot
persisted → compared against the most recent prior snapshot.

## 5. AI Fallback Chain Design

**Production chain (any code path touching real user data — transactions, balances, credit
info, categorization, recommendations):** Groq (Zero Data Retention enabled) → OpenRouter
(account-wide Zero Data Retention enabled, prompt-logging discount left disabled) → Cloudflare
Workers AI (does not train on Customer Content without explicit consent; only persists data
if explicitly paired with a storage product like R2, which this project does not use — stick
to Cloudflare's own hosted open-source models rather than proxied third-party models, since
proxied models carry no free allocation and bill at the source provider's rates). Exact
primary/fallback ordering is not fixed in this document — pending real rate-limit and latency
testing before implementation.

**Gemini — explicitly excluded from the production chain.** Free tier permits Google to train
on and have staff review submitted content, which disqualifies it from any call involving real
financial data, even redacted or aggregated (a recommendation call still reasons over real
spend/credit patterns, which is not "light" data despite not containing an account number or
name). Gemini may only be used on a separate, clearly-isolated code path for generic,
data-free content generation — e.g. general financial-literacy tips with no real user numbers
in the prompt — never for anything the AI Integration Layer routes as part of the production
chain above.

**Secrets:** each provider in the chain uses its own dedicated API key for this project, not
a key shared with Collins's other repositories — see `07-tech-stack.md` for the reasoning
(blast-radius isolation, per-project usage visibility, independent revocation).

## 6. Security & Privacy Design

Redaction happens before any network call to an external AI provider, at the
Ingestion-Service layer — this is the enforcement point, not a policy relying on trusting a
provider's terms of service. Provider-side zero-retention settings are a second line of
defense, not the primary control. Per-user data isolation is enforced at the repository layer
so that even a bug in a controller cannot leak another user's data through an unfiltered
query. Uploaded statements and receipts are stored in dedicated containers
(`finme-statements`, `finme-receipts`) within the Azure Blob Storage account, kept separate
from any other application's containers in the same account even where the account itself is
reused for cost reasons.
