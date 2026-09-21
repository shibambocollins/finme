# Conventions

## Comments explain why, not what

The single strongest convention in this codebase. A comment restating the line below it is
noise; a comment recording *why* a non-obvious choice was made is the thing that survives.

```java
// BAD - narrates the code
// Loop through the transactions and add up the amounts
for (Transaction t : transactions) { ... }

// GOOD - records a decision that is not visible in the code
// Income is money in that was never spending, so it is excluded from every spend figure.
// A refund is also money in, but it is NOT excluded: it reverses spending the user really
// did make, so it belongs in the totals as a negative.
```

Comment when: the choice has a non-obvious rationale, a constraint forced it, an alternative
was rejected for a reason, or a subtle failure mode is being guarded. Do not comment when the
code already says it plainly.

## Secrets

No API key, connection string or credential in source. Ever. Environment variables locally,
App Service configuration in production.

A separate key per provider for this project — not one shared with other repositories. If one
leaks, the blast radius should be this project alone.

## Financial math

Computed in code, never estimated by a model, and never in floating point. `BigDecimal` for
money, `DECIMAL(12,2)` in the schema.

The AI narrates and prioritises. If a figure appears on screen, deterministic code produced it
— [ADR-003](/decisions/ADR-003-deterministic-financial-math).

## Redaction

Account numbers, ID numbers and personal names are stripped before any external AI call. A
step in the pipeline, not a provider setting, so no path can bypass it —
[ADR-002](/decisions/ADR-002-redaction-before-ai-calls).

## Java

Standard Spring Boot conventions. Constructor injection, no field injection. Records for DTOs.
Lombok `@Getter`/`@Setter` on entities only.

Typed exceptions extending `ApiException`, each carrying its own status. No returning null to
signal an error.

## TypeScript

Strict mode. No `any` — if a type is genuinely unknown, `unknown` and narrow it.

Interfaces for API response shapes, declared next to the page that uses them.

One API wrapper, in `api/client.ts`. Pages do not call `fetch` directly.

## CSS

Plain CSS, tokens in `index.css`. Two rules that were learned the hard way:

**Media queries at the end of their section.** Equal specificity means source order decides. A
media block above the base rules it overrides silently loses — this made the calendar's entire
phone layout inert without any visible error.

**`minmax(0, 1fr)` for shrinkable grid columns.** Plain `1fr` floors each column at its
content width, which overflowed the calendar on every phone.

## Testing

Test behaviour, not implementation. A test asserting the shape of the code rather than what it
does breaks on every refactor and catches nothing.

**Backend** — 237 tests. Services directly with mocked repositories; `FeatureEndToEndHttpTest`
over real HTTP with a real JWT. `InlineBackgroundRunner` for async work: mocking
`BackgroundRunner` skips the work entirely and leaves assertions testing nothing, while a real
executor makes them race.

**Frontend** — 101 Playwright tests in real Chromium with every API call stubbed. jsdom cannot
measure layout, so it cannot catch the overflow bugs this suite exists to find.

Name tests as sentences about behaviour:

```java
void recordsFailureOnTheRowWithAReasonWhenAllVisionProvidersFail()
void statementNeverSupersedesACashReceiptEvenOnAnExactAmountMatch()
```

## Commits

Explain the reasoning, not just the change. A commit message is the only place a future reader
learns why.

```text
fix: Show dates in South African day-first format

The transaction table rendered the backend's ISO date verbatim, which is a wire
format shown to users. Everywhere else called toLocaleDateString() with no locale,
so the order followed the viewer's OS - a US-configured browser rendered 4 August
as 08/04/2026, indistinguishable from 8 April and wrong without ever looking wrong.

Written by hand rather than via toLocaleDateString('en-ZA'): Chromium's en-ZA
numeric format is yyyy/MM/dd, which is not what South Africans write.
```

See [Git workflow](/contributing/git-workflow).

## Scope

Follow the iteration order. Do not build later-iteration features before earlier ones work,
and do not silently expand scope — flag it instead.

When there is a genuine architectural choice, present the options with trade-offs rather than
picking one silently. When something in the docs looks wrong against real constraints, say so
rather than working around it quietly.
