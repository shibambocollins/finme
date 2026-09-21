# ADR-003: Financial math is deterministic, never AI-estimated

**Status:** Accepted
**Date:** 2026-08

## Context

The application already calls an LLM to structure and categorise transactions. Having it also
produce the totals — spend by category, credit utilisation, a monthly comparison — is a small
step from there, and it would remove code.

It would also be wrong.

## Decision

**Every figure shown to a user is computed in code.** The AI's role is narration and
prioritisation only.

| Computed | Model-generated |
| --- | --- |
| Total spend | "Groceries is your largest category this month" |
| Category breakdown | "You spent less than in July" |
| Monthly trend | Improvement plan ordering |
| Credit utilisation, overall and per account | Commentary on utilisation |
| Budget remaining and percentage | — |
| Calendar day totals | — |

If a number appears on screen, deterministic code produced it. The model may describe that
number; it never supplies it.

## Why

**A wrong total is worse than no total.** A user acting on a figure believes it. Getting
spending wrong by a plausible-looking margin is a failure that survives inspection — it looks
right, so nobody checks.

**Language models do arithmetic unreliably and confidently.** Not always wrong; wrong often
enough, with no signal distinguishing the wrong answers from the right ones. There is no
confidence score on a hallucinated sum.

**Nothing is reproducible.** The same statement could produce different totals on two runs.
Debugging "the dashboard said R4,200 yesterday" becomes impossible.

**It is not even hard.** Summing a list is a fold. Trading correctness for code that was never
difficult is a bad exchange.

## One definition, one file

The rule is enforced by `SpendMath`, the single definition of what counts as spending:

```java
static boolean affectsSpend(Transaction t) {
    return t.getDirection() != TransactionDirection.CREDIT
            || !INCOME.equalsIgnoreCase(categoryOf(t));
}

static BigDecimal contribution(Transaction t) {
    return t.getDirection() == TransactionDirection.CREDIT
            ? t.getAmount().negate()
            : t.getAmount();
}
```

Both the dashboard and the facts handed to the AI for narration derive from here. That is the
point: if they were computed separately they would eventually drift, and drift here means the
dashboard showing one total while the AI confidently narrates a different one — both plausible,
neither obviously wrong.

**Income is excluded; refunds are not.** Income is money in that was never spending. A refund
is also money in, but it reverses spending that really happened, so it belongs in the total as
a negative. That distinction is why `direction` is a stored field rather than inferred from
category: a grocery refund is correctly categorised "Groceries", and no category filter could
separate it from a grocery purchase.

## Credit utilisation

Same rule, and one ordering decision worth recording.

Accounts are ranked by **how much clearing each would reduce overall utilisation**, not by how
close each sits to its own limit. The two disagree often — a card at 95% of a small limit can
matter less than a modest balance against a large one. Both figures are reported, since an
account near its own limit matters independently.

Every analysis carries a disclaimer that is a **server-supplied constant, never model output**,
and is always rendered. A bad generation cannot reword or omit it.

## BigDecimal, not double

Money is `BigDecimal` in Java and `DECIMAL(12,2)` in the schema. Binary floating point cannot
represent decimal currency exactly, and the error compounds across a sum. This is not
theoretical — it is the oldest bug in financial software.

## Consequences

**Good.** Figures are correct, reproducible and debuggable. A user can verify a total by hand.
The model's failure modes affect commentary, not numbers.

**Bad.** More code than delegating to a prompt, and each new figure must be implemented rather
than asked for.

**Boundary.** Extraction is still AI-driven — reading "R372.98" off a statement line is
perception, not arithmetic, and is exactly what a model is good at. The line is drawn between
*reading a value* and *computing over values*. Everything past that line is code, which is why
the evaluation harness measures extraction accuracy specifically: that is where model error can
still reach a number.
