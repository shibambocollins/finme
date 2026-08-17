# FinMe — Risk Register

## R1 — Sensitive data reaching a third-party AI provider

Risk: account numbers, ID numbers, or personal names sent to an external AI provider despite
redaction logic.
Mitigation: field-level extraction plus regex/heuristic redaction as a safety net, enforced
before any network call, verified by dedicated unit tests. Provider-side zero-retention
settings as a second line of defense, not the primary control.

## R2 — AI provider policy changes over time

Risk: a provider's data-retention or training-use policy changes after this project is built,
silently reintroducing exposure.
Mitigation: provider policies were verified at planning time, not assumed from memory or
training data. Re-verify periodically rather than treating the current settings as permanent.

## R3 — Receipt/photo extraction unreliability

Risk: vision-LLM extraction from photographed receipts is inherently less reliable than
text-layer PDF extraction (lighting, crumpling, thermal-paper fading).
Mitigation: evaluation harness scores the photo pipeline separately from the PDF pipeline, so
this risk is measured rather than hidden inside a single blended accuracy figure. Manual entry
remains available as a fallback when extraction fails or is clearly wrong.

## R4 — Duplicate-matching false positive or false negative

Risk: two distinct transactions with the same amount on the same day could be incorrectly
matched as duplicates; conversely, a genuine duplicate with a slightly different amount
(rounding, tip) could be missed.
Mitigation: matches flag records as superseded rather than deleting them, preserving an audit
trail the user (or developer) can review. Documented as an accepted limitation rather than a
silently ignored edge case.

## R5 — AI credit recommendations creating false expectations

Risk: a user acts on AI-generated credit advice believing it guarantees a score improvement.
Mitigation: utilization and credit-position figures are always calculated deterministically,
never by the AI. A mandatory, visible disclaimer accompanies all AI-generated credit
recommendations.

## R6 — Manually entered credit data going stale

Risk: without automated bureau integration, a user's credit account data can drift out of
date, making analysis inaccurate.
Mitigation: periodic-update capability is a core requirement (FR-2.1.3), and progress-tracking
snapshots make staleness visible to the user rather than silent.

## R7 — Scope creep during solo development

Risk: as demonstrated during this planning process itself, continued engagement tends to
generate new features and modules faster than existing ones get finished, risking indefinite
non-completion.
Mitigation: this document set represents a deliberate scope freeze for Phase 1 and Phase 2.
New ideas arising during development are logged (e.g., Business Finance, already parked
separately) rather than immediately absorbed into current scope. The backlog in
`04-product-backlog.md` is the single source of what is being built next.

## R8 — Solo developer, no external deadline

Risk: without an external deadline, momentum can stall indefinitely.
Mitigation: iteration plan is ordered by dependency so each iteration ends in something
demonstrably working, providing a natural progress signal independent of calendar dates.

## R9 — Accidental real-data leakage through the Gemini dev-only path

Risk: Gemini is intentionally kept in the codebase for generic, data-free dev-only content
generation (see 03-system-design.md, Section 5), which creates a second AI code path
alongside the production chain. A future change could accidentally route real transaction,
balance, or credit data through that path, since it shares the same general AI Integration
Layer pattern as the safe chain.
Mitigation: the dev-only Gemini path should be implemented as a clearly separate method/class
from the production chain, not a configuration flag on the same call, so that routing real
data through it requires deliberately bypassing a type/interface boundary rather than just
flipping a setting. Covered by dedicated integration tests — see `05-test-plan.md`, Section 3.
