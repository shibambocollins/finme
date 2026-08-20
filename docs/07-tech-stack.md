# FinMe — Tech Stack & Resources

Split into **Decided** (confirmed, don't re-litigate) and **Open** (a real choice, made
deliberately at implementation time, not guessed at here).

## Decided

- **Backend:** Java, Spring Boot.
- **Frontend:** React, deployed on **Vercel**.
- **Database:** H2 (in-memory/file) for local development and testing, MySQL on Azure for
  production, switched via Spring profiles (`application-dev.properties` /
  `application-prod.properties`).
- **PDF text extraction:** Apache PDFBox — confirmed against a real Capitec statement
  (text-selectable, not scanned).
- **AI fallback chain (production / real financial data path):** Groq API (Zero Data
  Retention enabled) → OpenRouter (account-wide Zero Data Retention enabled, prompt-logging
  discount left off) → Cloudflare Workers AI (does not train on Customer Content without
  explicit consent; only persists data if explicitly paired with a storage product like R2 —
  use Cloudflare's own hosted open-source models, e.g. Llama/Mistral, to stay within the free
  Neuron allocation; proxied third-party models carry no free tier and bill at the source
  provider's rates). Exact primary/fallback order TBD based on real latency/rate-limit
  testing, not fixed here.
- **Gemini API — dev-only, non-financial use exclusively.** Free tier is explicitly excluded
  from any code path touching real user financial data (transactions, categorization,
  balances, credit info, spend recommendations) — free tier permits Google to train on and
  have staff review submitted content. Permitted use: generic, data-free content generation
  only (e.g. general financial-literacy tips with no real user numbers in the prompt).
- **Scheduled jobs:** Spring's built-in `@Scheduled` — sufficient at weekly cadence; Quartz
  is unnecessary complexity at this volume.
- **Redaction baseline:** regex/heuristic pass for account numbers and ID numbers, applied
  before any AI call. NER-based name redaction (e.g. Apache OpenNLP) is a stretch
  improvement once the extraction pipeline works, not a v1 requirement.
- **Hosting platform:** Azure, reusing existing account resources where possible. Blob
  Storage: a new dedicated container within the existing storage account (e.g.
  `finme-statements`, `finme-receipts`), not mixed into another app's container. App Service
  tier: start on Free (F1) during development against synthetic data; move to Basic (B1,
  ~$13/month) only once running regularly against real statements and the 60 CPU-min/day
  free cap becomes limiting. Confirm current pricing before committing — figures found during
  planning, not guaranteed current at build time.
- **Authentication:** JWT issued by the application after successful login; Google OAuth (via
  Spring Security's OAuth2 client support) as a login method feeding into that same JWT
  issuance — one token scheme regardless of how the user logs in.
- **CI:** GitHub Actions, added once the auth module is complete — runs tests/build on every
  push. This is CI only (reacting to pushes already made), not CD, and does not involve
  Claude Code touching GitHub in any way — see `CLAUDE.md` for the git workflow rule.
- **Testing frameworks:** JUnit and Mockito (backend), React Testing Library (frontend).
- **API documentation:** OpenAPI (springdoc-openapi for Spring Boot).
- **Secrets / API key management:** never commit API keys or database credentials to git.
  Environment variables via a git-ignored `.env` locally; Azure App Service configuration or
  Azure Key Vault when deployed. A separate API key per project per provider, generated under
  existing accounts — not the same key reused across FitNova/AI Job Assistant/ReviewLens/
  FinMe — for blast-radius isolation, per-project usage visibility, and independent
  revocation. Costs nothing extra: quota is scoped per-account, not per-key, on Groq,
  OpenRouter, and Cloudflare.
- **Geocoding provider:** OpenCage (2,500 req/day free, no card required). Chosen over Google
  Geocoding to avoid tying a personal-use portfolio project to Google billing and ToS caching
  restrictions — the free tier ceiling is well above solo-user receipt volume.
- **Map visualization library:** Mapbox GL JS (50k map loads/month free). Chosen over Leaflet
  for nicer default vector-tile styling in a portfolio piece where visual polish matters;
  requires a client-side token restricted by domain (standard practice, not a secret leak).

## Explicitly excluded

- **Supabase** — considered and rejected. The specific claim raised during planning (that
  Supabase sells user data) was checked and not supported by available evidence — Supabase
  holds SOC 2 Type 2, ISO 27001, and HIPAA compliance. Excluded on preference regardless,
  which is a sufficient reason on its own.

## Open — real decisions, not yet made

- **Vision-capable model for receipt/invoice extraction.** Which of Groq/OpenRouter/
  Cloudflare's routed models currently support vision input needs to be checked at
  implementation time — model support and pricing change, don't assume it matches whichever
  model handles text-based categorization.
- **Email/notification delivery.** SendGrid (free tier, less setup) vs. plain SMTP via Java
  Mail (no new dependency). Decide when building the notification iteration.
- **Charting library (React).** Recharts or Chart.js both fit; not decided.
- **Exact Azure compute service** (App Service vs. Container Apps vs. VM) beyond the F1→B1
  App Service path noted above.

## Deliberately not included (avoid over-engineering at this scale)

Dedicated observability/monitoring stack and formal backup strategy — appropriate for a team
running production software handling other people's money, not a solo personal-use v1.
Revisit only if the project's purpose changes (e.g. opened to other users).

## Resources referenced during planning (context only, not FinMe requirements)

- **iOS distribution research (AltStore, Expo EAS, native vs. PWA)** — explored before FinMe
  was chosen as the project, as a general question about your approach to iOS apps. Not
  re-confirmed specifically for FinMe. Revisit explicitly if FinMe ever needs iOS access
  beyond a responsive web app.
- **Experian API Hub / ClearScore research** — confirmed no public, self-service
  credit-bureau API is accessible at individual-developer tier; informs the manual-entry
  design in `02-srs.md`.
