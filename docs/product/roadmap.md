# Roadmap

The project was planned as nine ordered iterations across two phases, plus a post-plan pass
addressing usability gaps found while building. All of it is built. What follows records what
was delivered, what changed during the build, and what was deliberately left out.

## Phase 1 — Personal finance tracker

| # | Iteration | Delivered |
| --- | --- | --- |
| 1 | Core loop: auth, statement ingestion, basic categorisation | ✅ |
| 2 | Redaction and fallback chain hardening | ✅ |
| 3 | Dashboard v1 — charts and trends | ✅ |
| 4 | Receipt ingestion and duplicate detection | ✅ |
| 5 | Full dashboard — AI recommendations | ✅ |
| 6 | Notifications and evaluation harness | ✅ |
| 7 | Manual and cash entry via prompt | ✅ |

## Phase 2 — Credit module

| # | Iteration | Delivered |
| --- | --- | --- |
| 8 | Credit profile management | ✅ |
| 9 | Credit analysis engine | ✅ |

## Post-plan — usability

Four additions beyond the original nine, built 2026-08-26 to close gaps found during Phase 1
and 2, ahead of the visual redesign deferred until all iterations were done:

- Correcting a miscategorised or wrong transaction, so a bad extraction does not sit wrong in
  the record forever
- Searching and filtering the transaction list
- Monthly budgets per category with progress tracking
- A spending calendar, day by day

None of these involve AI. Transaction correction, filtering, budget tracking and calendar
totals are deterministic CRUD and arithmetic — consistent with the deterministic-math
principle by choice, not because a requirement forced it.

## Decisions taken during the build

Several things changed once real constraints appeared. They are recorded rather than quietly
absorbed:

**Map visualisation, descoped 2026-08-22.** Built during Iteration 5, then removed. Location
data on transactions was too unreliable to justify the surface area.

**Ranking by overall contribution, not per-account utilisation.** Credit accounts are ranked
by how much clearing each would reduce *overall* utilisation, not by how close each sits to
its own limit. The two disagree often. Both figures are reported, since an account near its
own limit matters independently.

**Email relay switched from Gmail to Brevo, 2026-08-28.** Gmail SMTP with an App Password
failed authentication on a from-scratch setup, surfacing the underlying problem: a personal
account repurposed as an application's mail relay is not a supported configuration.

**MySQL replaced by Azure SQL for production.** Driven by what Azure's free tier actually
offers — see [ADR-005](/decisions/ADR-005-azure-sql-over-mysql).

**Receipt ingestion made asynchronous.** Originally the receipt upload held the HTTP request
open for the full vision call while statements had always been asynchronous. Receipts now
match — [ADR-004](/decisions/ADR-004-asynchronous-ingestion).

## Out of scope

Deliberate exclusions, not omissions:

**Business finance.** A multi-tenant business ledger with accounting journals, AR/AP and
financial statement generation was considered and separated into its own future project,
connected to FinMe at most through a shared internal library — the extraction pipeline, the
evaluation methodology, the fallback chain — never a shared codebase.

**Direct bank connections.** No aggregator or Open Banking integration. Not available to a
solo project without a licensing relationship, and the document-upload path is what makes the
app work for paper receipts anyway.

**Multi-currency.** Amounts are rands. Currency is not a stored field.

**Gemini in the production chain.** Excluded on data-handling grounds regardless of tier —
[ADR-001](/decisions/ADR-001-ai-provider-fallback-chain).

## What is next

Feature work against the original plan is complete. Remaining effort is in three areas:

1. **Visual redesign**, deferred deliberately until all iterations were built
2. **Cold-start latency** on the free App Service tier —
   [Troubleshooting](/operations/troubleshooting#slow-first-request)
3. **Extending the evaluation harness** with a larger labelled set, since the measurement is
   the project's actual differentiator
