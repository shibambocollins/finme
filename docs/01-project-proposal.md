# FinMe — Project Proposal

## 1. Problem Statement

Manually tracking personal spending and credit health across bank statements, receipts, and
account information is tedious and error-prone. This makes it difficult to understand
spending patterns, monitor creditworthiness, catch overspending trends early, and make
informed financial decisions.

## 2. Objectives

- Automate transaction extraction from bank statement PDFs and photographed receipts/invoices,
  with accuracy measured against a labeled benchmark rather than assumed
- Provide accurate spend categorization and visualization (category breakdown, trends, map,
  recent activity)
- Support ad-hoc cash-transaction logging via natural-language prompt
- Deliver periodic (weekly) automated spend analysis and improvement suggestions
- Detect and resolve duplicate transactions arising from overlapping receipt and statement
  uploads
- Allow users to track credit accounts, balances, limits, and payment history, and receive
  AI-generated, explicitly non-guaranteed recommendations to improve credit utilization
- Enforce strict per-user data isolation and minimize exposure of sensitive personal data to
  third-party AI providers

## 3. Project Phases

**Phase 1 — Personal Finance Tracker.** Core ingestion, extraction, categorization,
dashboard, notifications, and evaluation harness. See Section 4 for detail.

**Phase 2 — Credit Score Module.** Built inside the same application and user account as
Phase 1 (not a separate system, no SSO required). See Section 4 for detail.

**Explicitly excluded from this project.** Business Finance (multi-tenant business ledger,
accounting journals, AR/AP, financial statement generation) was considered and deliberately
separated out as its own future project, connected to FinMe at most through a shared internal
library (extraction pipeline, eval harness methodology, AI fallback chain), not a shared
codebase. Reasoning: business-ledger correctness requirements, multi-tenancy, and compliance
scope are a materially different weight class from a personal tracker, and combining them
risked the same scope-growth pattern this proposal is written to avoid.

## 4. Scope

### In Scope — Phase 1

- User registration and login with real authentication; each user sees only their own data,
  enforced at the data-access layer
- Bank statement PDF upload; local text extraction via Apache PDFBox
- Receipt/invoice photo upload; extraction via a vision-capable LLM (separate pipeline from
  PDF extraction, since photographed documents cannot use text-layer extraction)
- Field-level extraction that selects only transaction-relevant data (date, merchant, amount,
  description, payment method) before any data is sent to an external AI provider
- PII redaction covering account numbers, ID numbers, and personal names, applied before any
  AI API call, independent of and in addition to provider-side data-retention settings
- LLM-based transaction structuring and categorization via an AI fallback chain (Groq,
  OpenRouter, Cloudflare Workers AI — all configured for zero/minimal data retention; provider
  order is an open implementation decision, not fixed in this document). Gemini is
  deliberately excluded from this chain — see Section 5.
- Manual/cash transaction entry via natural-language prompt
- Duplicate transaction detection between receipt-sourced and statement-sourced transactions
  for card payments only (cash transactions are never matched, since they have no statement
  counterpart); bank statement is authoritative when a match is found
- Spend dashboard: map-based visualization, category and time-trend charts, financial health
  summary, AI-generated recommendations, recent activity
- Weekly automated spend analysis and improvement suggestions, delivered by scheduled email
- Evaluation harness measuring extraction precision/recall, category accuracy, and
  hallucination rate, scored separately for the PDF pipeline and the photo pipeline

### In Scope — Phase 2

- Credit profile creation, optional, inside the existing user account (one login, one
  session — not a separate system)
- Manual entry of credit accounts, balances, limits, payment history, and score (no automated
  bureau API integration — confirmed not accessible at individual-developer tier; see
  Section 6)
- Deterministic (non-AI) calculation of credit utilization and overall credit position
- AI-generated narrative analysis, prioritized improvement plan, and "what-if" utilization
  simulation
- Mandatory, visible disclaimer that recommendations are not guaranteed to affect actual
  credit score
- Progress tracking and comparison between previous and current credit snapshots over time

### Out of Scope (v1)

- Business Finance / multi-tenant accounting (see Section 3)
- Family or shared account access — single-user-per-account only
- Automated bank account integration (Open Banking / direct API pull) — manual upload only
- Multi-currency support — ZAR only

## 5. Constraints

- Solo development, no fixed deadline; scope must remain realistic for one developer
- Must comply with data-handling decisions already made: local redaction before any external
  API call, provider zero/minimal-retention settings enabled. Gemini is used on free tier only
  (a deliberate cost decision), and free tier permits Google to train on and review submitted
  content — so Gemini is excluded entirely from any code path touching real financial data,
  and reserved only for generic, data-free dev-only content generation (see
  03-system-design.md, Section 5)
- Built on existing stack: Java / Spring Boot backend and MySQL database on Azure, React
  frontend on Vercel

## 6. Assumptions

- Bank statements are text-selectable PDFs, not scanned images. Confirmed for Capitec by
  direct inspection. Assumed to hold for other major SA banks, but not yet individually
  verified — verify per bank before building a parser around it.
- Single-user-per-account. No shared or family access planned.
- ZAR only, no multi-currency handling.
- Experian is the primary credit bureau referenced (matches dominant real-world SA usage,
  including bank-app integrations), with the schema generalized (`bureau`, `max_score`
  fields) rather than hardcoded, so other bureaus could be added later without a schema
  change.
- No public, self-service API exists for an individual developer to pull real consumer credit
  bureau data programmatically. Confirmed by research: available Experian APIs require an
  existing commercial relationship, contract, and approval process. Manual entry is the
  actual Phase 2 design, not a placeholder pending API access.
