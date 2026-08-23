# FinMe — Project Documentation

Personal finance and credit-health tracker. Solo, no fixed deadline, portfolio project.

## SDLC Approach

Hybrid: formal documentation written up front (this doc set), executed iteratively through
the backlog in `04-product-backlog.md`. Documents are living artifacts — when a requirement
changes during development, the relevant section here is updated and the change is noted,
rather than the whole set being treated as frozen forever.

## Document Set

- `01-project-proposal.md` — Problem statement, objectives, scope, constraints, assumptions
- `02-srs.md` — Functional and non-functional requirements, numbered and testable
- `03-system-design.md` — Architecture, components, data model, key flows
- `04-product-backlog.md` — Epics, user stories, iteration plan (ordered, not dated)
- `05-test-plan.md` — Testing strategy, including the AI extraction evaluation harness
- `06-risk-register.md` — Known risks and mitigations
- `07-tech-stack.md` — What's decided vs. still an open implementation choice

`CLAUDE.md`, at the repository root (not in this folder), gives Claude Code its working
rules and points back here for detail — see it for the git workflow and AI-provider rules
Claude Code must follow.

## Phase Summary

**Phase 1 — Personal Finance Tracker.** Upload bank statements (PDF) and receipts/invoices
(photo), automatic transaction extraction and categorization, spend dashboard (charts,
trends), cash-entry via natural language, weekly AI-generated spend analysis, measured
extraction accuracy via an evaluation harness.

**Phase 2 — Credit Score Module.** Optional credit profile inside the same user account.
Manual entry of credit accounts, balances, limits, score, payment history. Deterministic
utilization calculation, AI-narrated analysis and improvement plan (non-guaranteed), what-if
simulation, progress tracking over time.

**Not in this project.** Business Finance (multi-tenant business ledger/accounting) is saved
as a separate future project idea, connected to FinMe only via a shared internal library
(extraction pipeline, eval harness, AI fallback chain), not part of this codebase.
