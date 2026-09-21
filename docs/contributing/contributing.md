# Contributing

FinMe is a solo portfolio project. It is open to read, and the guidance here is what a
contributor — or future-you — needs to work in it without breaking its invariants.

## Before you start

Read [Conventions](/development/conventions). The rules there are enforced, not suggested.

The five that are non-negotiable:

1. Redaction before any external AI call
2. Financial math is deterministic, never model output
3. No secrets in source
4. No real financial data through Gemini, on any tier
5. Per-user isolation at the data-access layer, never in the UI

Each traces to a decision record in [Decisions](/decisions/ADR-001-ai-provider-fallback-chain).
Breaking one is a defect.

## Setting up

[Quick start](/getting-started/quick-start), then
[Local setup](/getting-started/local-setup) for AI keys, OAuth and email.

Use `AI_PROVIDER=mock` while working on anything that is not the extraction quality itself —
deterministic, instant, spends no quota.

## Making a change

**Understand the invariant first.** Most of this codebase has a reason behind it that is not
visible from the line you are editing. `SpendMath` lives in one file on purpose. `@Async` is on
a separate bean on purpose. Media queries sit at the end of a section on purpose. Each has a
comment saying why.

**Write the test that would have caught the bug.** The Playwright suite exists because
computing layout by hand was not enough — it found three real defects the arithmetic missed,
including an entire phone layout that was silently inert.

**Run both suites before committing:**

```bash
cd backend && ./mvnw test        # 237 tests
cd frontend && npm run test:e2e  # 101 tests
```

**Check the linter:**

```bash
cd frontend && npm run lint      # oxlint, not eslint
```

## Comments

Explain why, not what. A comment restating the code is noise; a comment recording why a
non-obvious choice was made is why this codebase can be picked up months later.

If you find yourself writing "this is weird because…", that is exactly the comment worth
keeping.

## Documentation

Docs live in `docs/` as Markdown, rendered by VitePress. Update them in the same change as the
code — documentation corrected later is documentation that stays wrong for a while.

```bash
npm run docs:dev     # local preview with search and navigation
npm run docs:build   # verify it builds
```

A change that alters behaviour described in the docs is not finished until the docs match.

## Architecture decisions

Anything with a real trade-off gets an ADR in `docs/decisions/` — what was decided, what was
rejected, and why. Follow the format of the existing five.

Add one when: the choice constrains future work, a reasonable person would ask "why not X",
or the reasoning depends on context that will not be obvious later.

## What not to do

- Do not add a second copy of a calculation. One definition, always.
- Do not bypass the API wrapper in `api/client.ts`.
- Do not take a user id from a request. It comes from the token.
- Do not commit `.env`, keys or connection strings.
- Do not expand scope silently. Flag it.

## Git

See [Git workflow](/contributing/git-workflow). Note in particular that remote operations —
pushing, PRs, merges — are performed by the repository owner.
