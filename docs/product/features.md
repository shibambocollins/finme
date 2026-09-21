# Features

## Ingestion

### Bank statement PDF

Upload a statement and the transactions in it become ledger entries. Text is extracted
locally with Apache PDFBox, redacted, split into chunks sized around provider rate limits,
then structured and categorised by the AI chain.

Processing is asynchronous: the upload returns `202 Accepted` immediately and the client
polls for progress. A large statement can take minutes, and holding an HTTP request open for
that long is not a workable design — [ADR-004](/decisions/ADR-004-asynchronous-ingestion).

A PDF with no extractable text is rejected explicitly rather than silently producing an empty
statement. That case is almost always a scan or a photo saved as a PDF, and the message says
so, because "0 transactions found" would look like the statement was simply empty.

### Receipt photo

Photographed receipts and invoices take a separate pipeline. There is no text layer to
extract, so the image goes to a vision-capable model directly. JPEG and PNG only — HEIC, the
iPhone default, is not confirmed supported by any provider in the chain and is rejected up
front rather than failing confusingly downstream.

Like statements, receipt extraction runs in the background and is polled.

### Manual and cash entry

Cash transactions never appear on a statement, so they are typed in as natural language and
parsed into a structured transaction. This is the only path where the user's own words are
the input.

### Duplicate detection

A card receipt and the statement line for the same purchase are the same transaction seen
twice. When a statement confirms a receipt-sourced card transaction, the receipt's entry is
marked `SUPERSEDED` rather than deleted, so the history stays honest.

Cash transactions are never matched this way, on purpose: cash has no statement line, so an
exact amount match is coincidence, not confirmation.

## Money

### Dashboard

Total spend, category breakdown, spend trend over time, and money in versus money out. Every
figure is computed in code from the transactions themselves.

Spend totals follow one rule, defined in a single place so the dashboard and the AI narration
can never disagree:

- Debits add to spend
- Income is excluded entirely — money in that was never spending
- Refunds are **not** excluded; they reverse real spending, so they subtract

That distinction is why transaction direction is a stored field rather than inferred from
category. A grocery refund is correctly categorised "Groceries", and no category filter could
tell it from a grocery purchase.

### Calendar

A month grid with per-day spend, shaded by intensity, alongside a summary rail showing month
total, active and no-spend days, the per-day average and the busiest day. Selecting a day
shows its transactions and a category split.

### Budgets

Monthly limits per category, with spend, remaining and percentage used. Over-budget
categories are marked distinctly rather than just coloured.

### Recommendations

AI-generated commentary on the dashboard figures. Fetched after the dashboard has already
rendered and never awaited alongside it — the totals and charts are correct whether or not
the commentary arrives, and this call may hit rate-limited third-party providers.

When no provider answers, the reason is shown rather than the section silently disappearing.

## Credit

### Profile and accounts

Credit accounts with balance, limit and payment status, under a profile carrying a bureau and
that bureau's own score ceiling — different bureaux use different scales, so the maximum is
stored per profile rather than hardcoded.

### Utilisation analysis

Overall utilisation and per-account utilisation, both computed in code. Accounts are ranked
by how much clearing each one would reduce overall utilisation, which is the number that
actually matters when deciding what to pay down first.

Every analysis carries a server-supplied disclaimer. It is a constant, never model output,
and always rendered.

### Simulation

Model the effect of a balance change on utilisation before making it.

### Score history

Recorded scores are kept rather than overwritten, so movement over time is visible.

## Account and data

- Email and password registration with verification, or Google OAuth
- Per-user data isolation enforced at the data-access layer, not by filtering in the UI
- CSV export of the visible transaction list
- Delete your data, or delete the account entirely — both guarded behind typing the account
  email, since neither is reversible

## Notifications

A weekly spending summary by email, scheduled Monday 07:00 Africa/Johannesburg. Configurable
via `WEEKLY_ANALYSIS_CRON` and disabled in the dev profile, so a local run never mails anyone.

## Measurement

The evaluation harness scores extraction against a labelled golden set, reporting precision,
recall and hallucination rate per pipeline. This is the feature the rest of the project exists
to support — see [Conventions](/development/conventions#testing).
