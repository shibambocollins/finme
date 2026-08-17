# FinMe — Product Backlog & Iteration Plan

No fixed deadline. Iterations are ordered by dependency and priority, not dated. Each
iteration should end in something runnable, not a partial slice of several features at once.

## Epics

1. Authentication & User Management
2. Statement Ingestion Pipeline
3. Redaction & Privacy Layer
4. AI Categorization & Fallback Chain
5. Manual/Cash Entry
6. Dashboard & Visualization (charts/trends first, map later)
7. Receipt Ingestion Pipeline
8. Duplicate Detection
9. Notifications
10. Evaluation Harness
11. Credit Profile Management (Phase 2)
12. Credit Analysis Engine (Phase 2)

## Iteration 1 — Core Loop: Auth + Statement Ingestion + Basic Categorization

Goal: a logged-in user can upload a statement and see extracted, categorized transactions.

- As a user, I want to register and log in, so that my financial data is private to me.
- As a user, I want to upload a bank statement PDF, so that I don't have to enter
  transactions by hand.
- As a user, I want the system to extract transactions from my statement automatically, so
  that I can see my spending without manual data entry.
- As a user, I want each transaction categorized (food, transport, etc.), so that I can
  understand where my money goes.

## Iteration 2 — Redaction & Fallback Chain Hardening

Goal: no sensitive data reaches an external AI provider under any code path, and extraction
survives a provider outage.

- As a user, I want my account number, ID number, and name kept out of any AI request, so
  that my sensitive data isn't exposed to a third party.
- As a developer, I want the AI integration to fall back to a secondary provider on failure,
  so that a single provider outage doesn't break ingestion.

## Iteration 3 — Dashboard v1 (Charts & Trends)

Goal: the extracted data becomes useful to look at.

- As a user, I want to see my total spend and category breakdown, so that I understand my
  spending habits at a glance.
- As a user, I want to see spend trends over time, so that I can spot changes in my habits.
- As a user, I want a recent-activity feed, so that I can quickly review my latest
  transactions.

## Iteration 4 — Receipt Ingestion & Duplicate Detection

Goal: users can log card and cash purchases from receipts without double-counting them when
the statement later confirms the same purchase.

- As a user, I want to upload a photo of a receipt, so that I can log a purchase immediately
  rather than waiting for my statement.
- As a user, I want the system to recognize when a receipt and a later statement transaction
  are the same purchase, so that it isn't counted twice.
- As a user, I want cash purchases to never be flagged as duplicates, since they won't appear
  on my statement.

## Iteration 5 — Map Visualization & Full Dashboard

Goal: complete the dashboard as originally scoped.

- As a user, I want to see my spending plotted on a map, so that I can visualize where I
  spend geographically.
- As a user, I want AI-generated recommendations shown alongside my dashboard, so that I get
  actionable insight, not just raw numbers.

## Iteration 6 — Notifications & Evaluation Harness

Goal: the system proactively surfaces insight, and extraction accuracy is measured, not
assumed.

- As a user, I want a weekly email summarizing my spending and suggesting improvements, so
  that I stay aware of my habits without checking the app constantly.
- As a developer, I want a labeled golden test set and a measured precision/recall/
  hallucination rate for both the PDF and photo extraction pipelines, so that I know how
  reliable the system actually is.

## Iteration 7 — Manual/Cash Entry via Prompt

Goal: transactions that never generate a receipt or statement line can still be logged.

- As a user, I want to describe a cash purchase in plain language, so that I can log it
  without a receipt or statement entry.

## Iteration 8 — Credit Profile Management (Phase 2 start)

Goal: users can optionally set up and maintain a credit profile.

- As a user, I want to create an optional credit profile inside my existing account, so that
  I don't need a separate login.
- As a user, I want to manually enter my credit accounts, balances, limits, and score, so
  that FinMe can analyze my credit position.

## Iteration 9 — Credit Analysis Engine

Goal: the credit module becomes genuinely useful, not just a data store.

- As a user, I want to see my credit utilization calculated accurately, so that I trust the
  numbers.
- As a user, I want a prioritized improvement plan with a clear disclaimer, so that I know
  what to focus on without false guarantees.
- As a user, I want to simulate how a balance change might affect my utilization, so that I
  can plan before making a payment.
- As a user, I want to compare my current credit snapshot to a previous one, so that I can
  see my progress over time.
