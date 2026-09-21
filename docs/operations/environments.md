# Environments

Two: `dev` and `prod`. Selected by `SPRING_PROFILES_ACTIVE`, defaulting to `dev`.

| | dev | prod |
| --- | --- | --- |
| Database | H2, file at `backend/data/finme` | Azure SQL Serverless |
| Schema | `ddl-auto=update` | `ddl-auto=update` |
| H2 console | Enabled at `/h2-console` | Disabled |
| CORS origin | `http://localhost:5173` | `finme.me`, `www.finme.me` |
| Weekly email | Disabled | Monday 07:00 SAST |
| SQL logging | Off | Off |
| Frontend | Vite dev server `:5173` | Vercel |

## Configuration precedence

Environment variable, then the profile-specific property file, then `application.properties`.
Locally, `backend/.env` supplies the variables.

No secret is committed at any level. A key in a source file is a bug.

## Production settings, and why each exists

Four settings in `application-prod.properties` each trace back to a specific production
failure. They are not boilerplate.

### Forwarded headers

```properties
server.forward-headers-strategy=framework
```

App Service terminates TLS at its edge and forwards over HTTP with `X-Forwarded-*`. Without
this, Spring builds OAuth `redirect_uri` and email-verification links from the internal
`http://host:8080` request instead of the public HTTPS host. Google rejects the callback with
`redirect_uri_mismatch`, and verification emails carry unreachable links.

Invisible locally — there is no proxy in front of the dev server.

### Connection timeout

```properties
spring.datasource.hikari.initialization-fail-timeout=60000
```

Azure SQL Serverless auto-pauses when idle. The first connection after a pause returns
"database not currently available" while it resumes, which takes 30–60 seconds. Without this,
context initialisation fails and the app cold-restarts into the same problem.

### Explicit dialect

```properties
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.SQLServerDialect
```

Otherwise Hibernate needs a live JDBC round-trip to detect it, and fails with
`Unable to determine Dialect without JDBC metadata` if the database is still resuming. Setting
it makes startup faster, deterministic and resilient to a briefly flaky connection.

### Schema update, not validate

```properties
spring.jpa.hibernate.ddl-auto=update
```

`validate` fails against an empty database with `Schema validation: missing table
[bank_statements]`. There is no Flyway or Liquibase in the project, so Hibernate creates the
schema on first deploy.

## Environment variables

### Required in production

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Must be `prod` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Azure SQL connection |
| `JWT_SECRET` | Token signing — 32+ random characters |
| `CORS_ALLOWED_ORIGINS` | Comma-separated exact origins |
| `AUTH_FRONTEND_REDIRECT_URI` | Where OAuth lands the browser |
| `AUTH_FRONTEND_LOGIN_URI` | Where verification redirects |

### AI providers

At least one is needed for extraction:

| Variable | Provider |
| --- | --- |
| `GROQ_API_KEY` | Groq — first in the chain |
| `OPENROUTER_API_KEY` | OpenRouter — second |
| `CLOUDFLARE_ACCOUNT_ID`, `CLOUDFLARE_API_TOKEN` | Cloudflare — third |

`GEMINI_API_KEY` is dev-only and never routes real financial data —
[ADR-001](/decisions/ADR-001-ai-provider-fallback-chain).

### Optional

| Variable | Default |
| --- | --- |
| `JWT_EXPIRATION_MS` | `86400000` (24h) |
| `AI_PROVIDER` | `chain` — set `mock` for a deterministic fake |
| `WEEKLY_ANALYSIS_CRON` | Monday 07:00 SAST; `-` disables |
| `EMAIL_VERIFICATION_TOKEN_EXPIRY_HOURS` | `24` |
| `GROQ_MODEL`, `OPENROUTER_VISION_MODEL`, … | Per-provider model overrides |

## CORS

Exact-match origins, comma-separated. No wildcards — `allowCredentials(true)` forbids them,
and the browser enforces that regardless of intent.

Both apex and `www` are listed, since either can be the origin depending on how a user arrives.

::: danger The failure mode is total and confusing
A mismatched origin blocks the preflight, so no API call ever reaches the server. The browser
reports a CORS error, the backend logs nothing at all, and every page appears broken for
reasons that look like a server outage. Verify the returned header, not just a 200 —
[Deployment](/operations/deployment#deployment-checklist).
:::
