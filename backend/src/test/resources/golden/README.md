# Golden set — extraction evaluation harness

Labeled source documents the harness scores both extraction pipelines against
(SRS FR-1.9.1, FR-1.9.2).

**Everything in this directory except this file is gitignored.** Real bank statements and
receipt photos contain real financial data and must never be committed to a public
repository. Nothing here is needed to run the build — when this directory is empty the
harness falls back to a generated synthetic set and says so in its report.

## Running it

```bash
cd backend
./mvnw test -Dtest=ExtractionEvaluationHarnessTest -Dlive.ai=true
```

It spends real API quota, which is why it is gated behind `-Dlive.ai=true` and never runs in a
normal `./mvnw test`. The report is printed and written to `target/extraction-evaluation.txt`,
so runs can be compared — a single accuracy figure only means something next to the previous
one.

## Adding a real document

Drop the source file in `statements/` or `receipts/`, alongside a `.json` label file:

```json
{
  "source": "july-2026-statement.pdf",
  "transactions": [
    {"date": "2026-07-02", "merchant": "WOOLWORTHS SANDTON CITY", "amount": "842.15", "category": "Groceries"},
    {"date": "2026-07-03", "merchant": "UBER TRIP CAPE TOWN",     "amount": "87.50",  "category": "Transport"}
  ]
}
```

- `amount` is a **string**, not a JSON number. Routing money through a double is the precision
  loss this project avoids everywhere else; the ground truth is the last place to introduce it.
- `mimeType` is optional — it defaults to `application/pdf` for statements and `image/jpeg` for
  receipts.
- Label **every** transaction in the document, including income and refunds. Extraction is
  supposed to find those; excluding them from the labels would score correct extractions as
  hallucinations. Deciding what counts as *spending* happens later, in `SpendMath`.

## How scoring works

A reported transaction matches a labeled one when **date and amount agree exactly**. Merchant
text is deliberately not part of the match — models paraphrase it constantly ("WOOLWORTHS
SANDTON CITY" comes back as "Woolworths"), and scoring on string equality would turn a naming
difference into a fabricated accuracy problem. Category is scored separately, over matched
transactions only, so a detection failure is not counted twice.

Once real documents are present the report labels the set `real, labeled` instead of
`SYNTHETIC`. Treat synthetic scores as a floor on difficulty: generated documents have no scan
skew, no column drift, and no faded thermal print.
