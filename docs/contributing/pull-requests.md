# Pull requests

## Before opening

- [ ] Backend suite green — `./mvnw test`
- [ ] Frontend suite green — `npm run test:e2e`
- [ ] Linter clean — `npm run lint`
- [ ] Docs updated if behaviour changed
- [ ] No secrets in the diff
- [ ] Commit messages explain the reasoning

## Description

Answer three questions:

**What was wrong?** Concretely. "The transaction table rendered the backend's ISO date
verbatim" beats "date formatting was incorrect".

**Why this fix?** Especially if an obvious alternative was rejected. "Written by hand rather
than `toLocaleDateString('en-ZA')`, because Chromium's en-ZA format is `yyyy/MM/dd`" tells the
reviewer something they would otherwise have to discover.

**What is deliberately unchanged?** "CSV export keeps ISO, since `04/08/2026` is read as
8 April by a US-locale spreadsheet" prevents a reviewer from flagging it as an oversight.

### Template

```markdown
## What

One or two sentences.

## Why

The actual problem, and why this approach.

## Deliberately not changed

Anything that looks like an oversight but is not.

## Testing

What was run, and what new tests were added.
```

## Scope

One concern per PR. A fix plus an unrelated refactor is two PRs — a reviewer cannot
meaningfully assess a diff where the important change is buried in formatting noise.

If a drive-by fix is genuinely necessary, say so in the description rather than leaving it to
be discovered.

## What review looks for

**Invariants.** Does it preserve redaction before AI calls, deterministic math, data
isolation, no secrets in source?

**Duplication of truth.** Is a calculation now defined in two places? `SpendMath` is one file
for a reason — the failure mode of drift is the dashboard and the AI narration disagreeing,
both plausibly.

**Tests that test behaviour.** A test asserting the shape of the implementation breaks on every
refactor and catches nothing.

**Comments explaining why.** A non-obvious choice without a comment is a choice that will be
undone by someone who does not know it was deliberate.

**Error handling that says what happened.** Swallowing an exception or replacing a server's
message with "Something went wrong" discards the only useful information.

## CI

GitHub Actions runs on every push and on PRs into `main`. A red build is not merged.

Concurrency cancels superseded runs — on a free minute budget there is no value finishing a
build for a commit nobody will look at again.

## Merging

Merge in **one** place, either the PR or locally, never both. Doing both produces identical
content under two SHAs and leaves the branches diverged for no real reason — see
[Git workflow](/contributing/git-workflow#merging).
