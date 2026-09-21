# Local setup

[Quick start](/getting-started/quick-start) gets the app running. This page covers the
optional configuration that turns on the parts that call out to other services.

## Configuration model

The backend reads configuration from environment variables, with defaults in
`application.properties`. Local values go in `backend/.env`, which is gitignored.

Profiles select which property file applies on top of the base:

| Profile | Selected by | Database |
| --- | --- | --- |
| `dev` (default) | nothing — it is the fallback | H2, file-based at `backend/data/finme` |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | Azure SQL, from `DB_URL` |

::: tip
Nothing is hardcoded and no secret is committed. If you find a key in a source file, that is a
bug — see [Conventions](/development/conventions#secrets).
:::

## AI providers

Extraction needs at least one provider. The chain tries them in order and falls through on
failure, so one key is enough to work with:

```properties
GROQ_API_KEY=
OPENROUTER_API_KEY=
CLOUDFLARE_ACCOUNT_ID=
CLOUDFLARE_API_TOKEN=
```

All three have free tiers. Groq is first in the chain on latency;
[ADR-001](/decisions/ADR-001-ai-provider-fallback-chain) explains the ordering and why Gemini
is excluded from production regardless of what key is present.

Models are configurable and have working defaults:

| Provider | Text model | Vision model |
| --- | --- | --- |
| Groq | `openai/gpt-oss-20b` | `qwen/qwen3.6-27b` |
| OpenRouter | `meta-llama/llama-3.3-70b-instruct:free` | `google/gemma-4-31b-it:free` |
| Cloudflare | `@cf/meta/llama-3.1-8b-instruct` | `@cf/meta/llama-3.2-11b-vision-instruct` |

Override any of them with `GROQ_MODEL`, `OPENROUTER_VISION_MODEL`, and so on.

### Working without provider keys

Set `AI_PROVIDER=mock` to use a deterministic fake. Useful for working on ingestion flow,
duplicate detection or the UI without spending quota or waiting on a network call.

::: danger Use a separate key per provider for this project
Not a key shared with your other repositories. If one leaks, the blast radius should be this
project only.
:::

## Email verification locally

Registration issues a verification token. Without SMTP configured no email is sent, so there
are two ways through:

**Read the token from the database.** Open <http://localhost:8080/h2-console> (JDBC URL
`jdbc:h2:file:./data/finme`, user `sa`, no password) and run:

```sql
SELECT email, verification_token FROM users ORDER BY id DESC;
```

Then visit `http://localhost:8080/api/auth/verify-email?token=<the-token>`.

**Or flip the flag directly:**

```sql
UPDATE users SET email_verified = TRUE WHERE email = 'you@example.com';
```

### Configuring real email

```properties
SMTP_USERNAME=
SMTP_PASSWORD=
MAIL_FROM_ADDRESS=
```

The project uses Brevo's free SMTP relay. It was originally Gmail with an App Password, which
failed authentication on a from-scratch setup — a personal Gmail account repurposed as an
application's mail relay is not a supported configuration.

## Google OAuth

```properties
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
```

In the Google Cloud Console, add this exact authorised redirect URI:

```
http://localhost:8080/login/oauth2/code/google
```

::: warning The redirect URI points at the backend, not the frontend
Google calls Spring Security's callback, which then redirects the browser to the frontend.
Pointing it at `:5173` produces `redirect_uri_mismatch`, and it is the single most common
mistake in this setup.
:::

Scopes are Spring's defaults for Google — `openid`, `profile`, `email`. Nothing sensitive, so
no Google verification review is required.

## The weekly email job

Disabled in the dev profile so a local run never mails anyone. The schedule lives in
`WEEKLY_ANALYSIS_CRON`; production runs Monday 07:00 Africa/Johannesburg.

## Optional overrides

```properties
JWT_EXPIRATION_MS=86400000            # 24h
BACKEND_BASE_URL=http://localhost:8080
AUTH_FRONTEND_REDIRECT_URI=http://localhost:5173/auth-callback
AUTH_FRONTEND_LOGIN_URI=http://localhost:5173/login
EMAIL_VERIFICATION_TOKEN_EXPIRY_HOURS=24
```

## Resetting local data

The dev database is a file. Delete it and the schema is recreated empty on next start:

```bash
rm -rf backend/data
```
