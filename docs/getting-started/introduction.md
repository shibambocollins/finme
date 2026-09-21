# Introduction for developers

What you need to know before changing anything, in the order it becomes relevant.

## The shape of the repository

```text
finme/
├── backend/          Spring Boot 4, Java 21
│   └── src/main/java/com/finme/backend/
│       ├── ai/           Provider chain, extraction contracts
│       ├── controller/   HTTP endpoints
│       ├── dto/          Request and response records
│       ├── entity/       JPA entities
│       ├── exception/    Typed exceptions, global handler
│       ├── repository/   Spring Data repositories
│       ├── security/     JWT, OAuth2, authenticated-user lookup
│       └── service/      Business logic
├── frontend/         React 19, TypeScript, Vite
│   ├── e2e/              Playwright suite
│   └── src/
│       ├── api/          Fetch wrapper, error type
│       ├── auth/         Auth context, JWT decoding
│       ├── components/   Shared components
│       ├── pages/        One file per route
│       └── utils/        Formatting, CSV
└── docs/             This documentation
```

## The five rules that are not negotiable

These are enforced in code and in review. Breaking one is a defect, not a style disagreement.

1. **Redaction before any external AI call.** Account numbers, ID numbers and personal names
   are stripped first — [ADR-002](/decisions/ADR-002-redaction-before-ai-calls).
2. **Financial math is deterministic.** Totals and utilisation are computed in code; the AI
   narrates only — [ADR-003](/decisions/ADR-003-deterministic-financial-math).
3. **No secrets in source.** Environment variables locally, App Service configuration in
   production.
4. **Never route real financial data through Gemini**, on any tier — it is dev-only, for
   generic content with no user data in the prompt.
5. **Per-user isolation is enforced at the data-access layer**, never by filtering in the UI.

## Where the interesting code lives

| If you are changing... | Start at |
| --- | --- |
| How a statement becomes transactions | `service/StatementIngestionService.java` |
| How a receipt photo becomes transactions | `service/ReceiptIngestionService.java` |
| What counts as spend | `service/SpendMath.java` |
| Provider ordering and failover | `ai/FallbackAiProviderChain.java` |
| What gets stripped before an AI call | `service/RedactionService.java` |
| Credit utilisation | `service/CreditUtilization.java` |
| Who the current user is | `security/AuthenticatedUser.java` |

`SpendMath` is worth reading early regardless of what you are working on. It is the single
definition of what counts as spending, kept in one place deliberately: the income and refund
rules are subtle enough that a second copy would drift, and the failure mode of drift is the
dashboard showing one total while the AI narrates a different one, both looking plausible.

## Running the tests

```bash
cd backend && ./mvnw test                  # 237 tests
cd frontend && npm run test:e2e            # 101 browser tests
```

The frontend suite runs real Chromium against a dev server with every API call stubbed. It
needs no backend and touches no deployed infrastructure. jsdom was rejected for this: it
reports every element as 0×0, so it cannot catch layout overflow — which is exactly the class
of bug the suite exists to find.

## Conventions worth knowing before your first commit

- **Comments explain why, not what.** The codebase comments decisions, trade-offs and
  non-obvious constraints. It does not narrate what the line below does.
- **Commit messages explain the reasoning**, not just the change.
- **Remote git operations are done by the repository owner.** Work on local branches and make
  local commits; pushing, PRs and merges are handled manually.

Full detail in [Conventions](/development/conventions) and
[Git workflow](/contributing/git-workflow).

## Next

- [Quick start](/getting-started/quick-start) — get it running
- [Architecture](/development/architecture) — how requests flow
- [Backend](/development/backend) and [Frontend](/development/frontend) — layer by layer
