# Users

## Who this is for

FinMe is built for an individual managing their own money — not a household with shared
accounts, not a business, not an accountant handling clients. One person, one account, their
own documents.

The design assumptions that follow from that are worth being explicit about, because several
architectural choices depend on them:

- **One user owns all their data, and sees only their own.** Isolation is enforced at the
  data-access layer. There is no sharing model, no roles, no delegated access.
- **Documents come from the user, not from a bank connection.** No aggregator, no Open
  Banking API. The user uploads what they have.
- **Amounts are in rands.** Currency is not a stored field, and the app does not convert.

## Primary user

**Someone who wants to know where their money went, without doing the bookkeeping.**

They have bank statements they can download and receipts they collect. They will not
hand-enter fifty transactions a month, which is exactly why the extraction pipeline exists —
a statement upload fills a whole month in one action.

What they need from the app:

- Upload a statement, get a categorised month
- See totals and trends without interpreting a spreadsheet
- Catch a category running over budget before month end
- Understand their credit utilisation and what would move it

## Secondary user: the person with a credit question

The credit module serves a narrower need. Someone tracking accounts across lenders wants to
know their overall utilisation and which balance to clear first — a question that is
arithmetic, not advice, and is answered as such.

The distinction matters and is enforced in the code: utilisation is computed deterministically
and the AI only narrates and prioritises. Every analysis carries a disclaimer that is a
server-supplied constant, never model output. See
[ADR-003](/decisions/ADR-003-deterministic-financial-math).

## Who this is not for

**Businesses.** A multi-tenant business ledger — accounting journals, AR/AP, financial
statement generation — was considered and deliberately separated into its own future project.
Business-ledger correctness, multi-tenancy and compliance scope are a materially different
weight class, and combining them risked exactly the scope growth the project was planned to
avoid.

**Anyone needing financial advice.** The app reports figures and prioritises actions. It does
not advise, and says so wherever it could be mistaken for doing otherwise.

## Trust boundary

Users hand this application bank statements. That shapes two non-negotiable behaviours:

1. **Redaction before any external call.** Account numbers, ID numbers and personal names are
   stripped before data reaches an AI provider —
   [ADR-002](/decisions/ADR-002-redaction-before-ai-calls).
2. **No provider retains the data.** Every provider in the chain is configured for zero or
   minimal retention, and Gemini is excluded from the production chain entirely on those
   grounds — [ADR-001](/decisions/ADR-001-ai-provider-fallback-chain).
