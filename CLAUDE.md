# FinMe — Claude Code Project Instructions

## What this project is

FinMe is a personal finance and credit-health tracker. Solo project, no fixed deadline,
built by Collins (final-year IT/Application Development student) as a portfolio piece with
real technical depth — not just another CRUD finance app. Its actual differentiator is a
measured AI extraction pipeline (evaluation harness with precision/recall/hallucination-rate
tracking), not the dashboard.

Full planning docs live in this project's `docs/` folder (`00-README.md` through
`07-tech-stack.md`). Read `00-README.md` first for the index. This file does not repeat
their content — it holds the rules you should follow every session and points you to where
the actual detail lives.

## Rules — follow every session

- **Never hallucinate.** Don't invent Spring Boot behavior, library APIs, method signatures,
  or dependency versions. If uncertain, say so explicitly and verify against real
  documentation or the installed package before using it.
- **Redaction is a hard security requirement, not a style preference.** Account numbers, ID
  numbers, and personal names must be stripped before any data reaches an external AI
  provider call. See `02-srs.md`, NFR-2.
- **Never route real financial data through Gemini, on any tier.** Gemini is dev-only, for
  generic content with no real user data in the prompt — see `02-srs.md`, NFR-4. The
  production chain for real data is Groq → OpenRouter → Cloudflare Workers AI.
- **Financial math is deterministic, never AI-estimated.** Credit utilization and any other
  calculated figure must be computed in code. The AI's role is narration and prioritization
  only — see `02-srs.md`, FR-2.2.1.
- **Never hardcode or commit API keys, database credentials, or other secrets.** Use
  environment variables locally and Azure App Service configuration / Key Vault when
  deployed — see `07-tech-stack.md`.
- **Follow the iteration order in `04-product-backlog.md`.** Don't build later-iteration
  features before earlier ones work, and don't silently expand scope beyond
  `01-project-proposal.md` — flag it back to Collins instead of just doing it.
- **When there's a genuine architectural choice, present options with trade-offs** rather
  than silently picking one and presenting it as the only path.
- **Say directly when something in these docs looks wrong or outdated** once you're actually
  building against real constraints (e.g., a provider's rate limits, a library's real
  behavior) — don't quietly work around a bad assumption, flag it back.
- **Separate known facts from assumptions and recommendations** when explaining a technical
  decision.
- **Never push to GitHub, open a pull request, or merge anything.** You may create and work
  on local branches and make local commits when asked, but all remote git operations —
  pushing, opening PRs, merging — are done manually by Collins. You are not a collaborator on
  this repository.
- **Use a separate API key per provider for this project**, not one shared across Collins's
  other repositories — see `07-tech-stack.md` for why.

## How Collins works

- Solo developer, primarily Java/Spring Boot, React, SQL/MySQL, REST APIs, Git/GitHub, Azure.
- No fixed deadline — prioritize each iteration actually working over speed.
- Wants the reasoning, not just the code — explain non-trivial decisions, don't just execute.
- Wants pushback when an idea is wrong or adds unnecessary complexity for a solo/portfolio
  project — agreement is not the goal, correctness is.

## Where things are

- `01-project-proposal.md` — problem, objectives, scope, constraints, assumptions
- `02-srs.md` — numbered functional and non-functional requirements
- `03-system-design.md` — architecture, components, data model, key flows
- `04-product-backlog.md` — epics, user stories, build order (ordered, not dated)
- `05-test-plan.md` — testing strategy, including the extraction evaluation harness
- `06-risk-register.md` — known risks and mitigations
- `07-tech-stack.md` — what's decided vs. still an open implementation choice
