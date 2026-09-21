# Quick start

Get FinMe running locally in about five minutes. No database to install — the dev profile
uses a file-based H2 database created on first run.

## Prerequisites

| Tool | Version | Check |
| --- | --- | --- |
| JDK | 21 | `java -version` |
| Node.js | 20+ | `node -v` |
| Git | any | `git --version` |

Maven is not required separately — the repository ships the Maven wrapper (`mvnw`).

## 1. Clone

```bash
git clone https://github.com/shibambocollins/finme.git
cd finme
```

## 2. Configure the backend

```bash
cp backend/.env.example backend/.env
```

Open `backend/.env` and set **one** required value:

```properties
JWT_SECRET=any-long-random-string-at-least-32-characters
```

Everything else can stay empty for a first run. Without AI provider keys the app starts and
works — you can register, log in, and enter transactions manually. Statement and receipt
extraction need at least one provider key; see [Local setup](/getting-started/local-setup#ai-providers).

::: warning
Never commit `backend/.env`. It is gitignored, and it is the file that will hold real keys.
:::

## 3. Configure the frontend

```bash
cp frontend/.env.example frontend/.env
```

The default already points at the local backend:

```properties
VITE_API_BASE_URL=http://localhost:8080
```

## 4. Run both

Two terminals.

**Backend** — starts on `:8080`, creates `backend/data/finme.mv.db` on first run:

```bash
cd backend
./mvnw spring-boot:run
```

**Frontend** — starts on `:5173`:

```bash
cd frontend
npm install
npm run dev
```

## 5. Open it

<http://localhost:5173>

Register an account. In the dev profile email verification is not enforced by an outbound
mail — see [Local setup](/getting-started/local-setup#email-verification-locally) for how to
verify without configuring SMTP.

## Verify it works

```bash
# Backend suite — 237 tests
cd backend && ./mvnw test

# Frontend suite — 101 browser tests, needs a one-time Chromium download
cd frontend && npx playwright install chromium && npm run test:e2e
```

## Useful local URLs

| URL | What |
| --- | --- |
| <http://localhost:5173> | The app |
| <http://localhost:8080/swagger-ui.html> | Interactive API documentation |
| <http://localhost:8080/h2-console> | H2 database console (dev profile only) |

For the H2 console, the JDBC URL is `jdbc:h2:file:./data/finme`, user `sa`, no password.

## Next

- [Local setup](/getting-started/local-setup) — AI providers, Google OAuth, email
- [Architecture](/development/architecture) — how the pieces fit
- [Conventions](/development/conventions) — before writing code
