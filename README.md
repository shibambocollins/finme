# FinMe

Personal finance and credit-health tracker. Extracts transactions from bank statement PDFs and
receipt photos, categorises them, and reports credit utilisation — with extraction accuracy
measured against a labelled benchmark rather than assumed.

**Live:** <https://finme.me> · **Docs:** [`docs/`](docs/index.md)

## What it does

- **Statement and receipt ingestion** — upload a PDF or photograph a receipt; transactions are
  extracted, categorised and deduplicated
- **Measured extraction** — an evaluation harness scores both pipelines on precision, recall
  and hallucination rate
- **Redaction before any AI call** — account numbers, ID numbers and names are stripped before
  data reaches a provider
- **Deterministic financial math** — every figure is computed in code; the AI narrates, it
  never calculates
- **Dashboard, calendar and budgets** — spend totals, trends, day-by-day view, category limits
- **Credit module** — utilisation, a prioritised improvement plan, and balance-change
  simulation

## Stack

| | |
| --- | --- |
| Backend | Spring Boot 4, Java 21, Spring Security, JPA |
| Frontend | React 19, TypeScript, Vite |
| Database | Azure SQL Serverless (H2 locally) |
| AI | Groq → OpenRouter → Cloudflare Workers AI, with fallback |
| Extraction | Apache PDFBox for local PDF text |
| Tests | JUnit 5 (237) · Playwright (101) |
| Hosting | Azure App Service · Vercel |

## Quick start

```bash
git clone https://github.com/shibambocollins/finme.git
cd finme

cp backend/.env.example backend/.env    # set JWT_SECRET
cp frontend/.env.example frontend/.env

cd backend && ./mvnw spring-boot:run    # :8080
cd frontend && npm install && npm run dev   # :5173
```

No database to install — the dev profile uses file-based H2. Full setup, including AI keys and
OAuth, in [Quick start](docs/getting-started/quick-start.md).

## Tests

```bash
cd backend && ./mvnw test
cd frontend && npx playwright install chromium && npm run test:e2e
```

## Documentation

```bash
npm install && npm run docs:dev
```

| Section | |
| --- | --- |
| [Product](docs/product/overview.md) | What it is, who it's for, what shipped |
| [Getting started](docs/getting-started/quick-start.md) | Run it locally |
| [Development](docs/development/architecture.md) | Architecture, backend, frontend, database |
| [API](docs/api/overview.md) | Endpoints, auth, errors |
| [Operations](docs/operations/deployment.md) | Deployment, environments, troubleshooting |
| [Decisions](docs/decisions/ADR-001-ai-provider-fallback-chain.md) | Why things are built this way |

---

Built by Collins Shibambo.
