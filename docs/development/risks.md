# Known risks and limitations

What could go wrong, what is being done about it, and what is accepted rather than solved.
Naming a limitation is not the same as having no answer for it, but pretending it does not
exist would be worse than either.

## Data exposure

### Sensitive data reaching a provider

Account numbers, ID numbers or names sent to an external AI provider despite redaction.

**Mitigation.** Field-level extraction plus regex and heuristic redaction as a safety net,
enforced before any network call and verified by dedicated unit tests. Provider-side
zero-retention settings are a second line, never the primary control —
[ADR-002](/decisions/ADR-002-redaction-before-ai-calls).

Redaction is tested directly rather than only through the pipeline, because a regression here
is silent: nothing fails, data simply leaves that should not have.

### Provider policy drift

A provider's retention or training-use policy changes after the project is built, silently
reintroducing exposure.

**Mitigation.** Policies were verified at planning time against live documentation rather than
assumed. They need re-verifying periodically — current settings are not permanent, and nothing
notifies you when they change.

### The Gemini dev-only path

Gemini is kept for generic, data-free development work. That creates a second AI code path
beside the production chain, and a future change could route real data through it.

**Mitigation.** It is a separate class, not a configuration flag on the same call. Routing real
data through it requires deliberately crossing a type boundary rather than flipping a setting —
a mistake you have to work at, instead of one you can make by accident.

## Extraction quality

### Photo extraction is less reliable than PDF

Vision extraction from a photographed receipt is inherently weaker than text-layer extraction:
lighting, crumpling, faded thermal paper.

**Mitigation.** The evaluation harness scores the photo pipeline **separately** from the PDF
pipeline, so this is measured rather than hidden inside one blended accuracy figure. Manual
entry remains available when extraction fails or is visibly wrong, and any transaction can be
corrected.

### Duplicate matching, both directions

Two distinct transactions with the same amount on the same day can be matched incorrectly.
Conversely a genuine duplicate with a slightly different amount — rounding, a tip — can be
missed.

**Mitigation.** Matches mark records `SUPERSEDED` rather than deleting them, preserving an
audit trail that can be reviewed and reversed. Cash transactions are never matched at all,
since cash has no statement line and an exact amount match is coincidence rather than
confirmation.

Documented as an accepted limitation, not a silently ignored edge case.

## Credit module

### Advice being read as a guarantee

A user acts on AI-generated credit commentary believing it guarantees an outcome.

**Mitigation.** Every figure is computed deterministically, never by the model
([ADR-003](/decisions/ADR-003-deterministic-financial-math)). A disclaimer accompanies all
AI-generated credit output, supplied as a server-side constant that a bad generation cannot
reword or omit.

### Manually entered data going stale

Without bureau integration, credit account data drifts out of date and analysis silently
degrades.

**Mitigation.** Updating is a first-class capability, and score snapshots make staleness
visible to the user rather than invisible.

## Operations

### No monitoring

There is no uptime monitoring, error aggregation, metrics or alerting. Failures are currently
found by someone using the application.

Known and unmitigated. [Monitoring](/operations/monitoring#what-to-add-first) sets out what to
add first and why.

### Cold starts on free tiers

The first request after an idle period takes 30 seconds to 3 minutes. Two causes compound: the
App Service free tier unloads the app, and Azure SQL Serverless auto-pauses.

Partly mitigated by raising the auto-pause delay. Fully solvable only by paying for compute —
[Troubleshooting](/operations/troubleshooting#slow-first-request).

### Schema management without migrations

`ddl-auto=update` creates and extends the schema but never drops or narrows. Adequate for a
solo project with no production data migrations so far; **not** adequate for a team or for data
that matters.

Introducing Flyway or Liquibase is the first thing to do if either becomes true.

### Dev and prod run different database engines

H2 locally, SQL Server in production. JPA abstracts most of the difference, but not all of it.
Accepted deliberately — [ADR-005](/decisions/ADR-005-azure-sql-over-mysql#consequences).

## Project

### Scope creep

Demonstrated during planning itself: continued engagement generates new features faster than
existing ones get finished, risking indefinite non-completion.

**Mitigation.** The documented scope is a deliberate freeze. New ideas are logged rather than
absorbed — Business Finance was parked as its own future project rather than folded in. The
roadmap is the single source of what gets built next.

### No external deadline

Solo, with no deadline, momentum can stall.

**Mitigation.** Iterations are ordered by dependency so each ends in something demonstrably
working, which provides a progress signal independent of the calendar.
