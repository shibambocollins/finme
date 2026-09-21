# Troubleshooting

Real failures encountered in this deployment, with what actually caused them.

## Everything fails instantly with a CORS error

**Symptom.** Every API call fails in well under a second. The browser console reports
`No 'Access-Control-Allow-Origin' header is present`. The Network tab shows `CORS error`,
0 bytes, 0 response headers. Backend logs show *nothing at all*.

**Cause.** `CORS_ALLOWED_ORIGINS` does not include the origin the browser is on. The preflight
`OPTIONS` is rejected, so no request ever reaches the application — which is why the logs are
empty and why it fails too fast to be a server problem.

**Diagnose:**

```bash
curl -s -i -X OPTIONS "https://finme-backend.azurewebsites.net/api/dashboard/summary" \
  -H "Origin: https://finme.me" \
  -H "Access-Control-Request-Method: GET" | grep -i "^access-control"
```

No `Access-Control-Allow-Origin` in the output confirms it.

**Fix.** Add the origin, restart, and verify the header comes back — not just a `200`.

::: warning
This masquerades as an application bug. Uploads "fail", the dashboard "won't load", nothing is
logged. It is worth ruling out first whenever several unrelated features break at once.
:::

## Slow first request {#slow-first-request}

**Symptom.** The first request after a quiet period takes 30 seconds to 3 minutes. Everything
afterwards is fast. Google sign-in is the worst affected.

**Cause.** Two things asleep, compounding:

| Component | Setting | Cost |
| --- | --- | --- |
| App Service | F1 Free, no Always On | Unloads after ~20 min idle; full Spring boot on next hit |
| Azure SQL | Serverless auto-pause | 30–60s to resume |

OAuth suffers most because it is a redirect chain. The backend can go cold *while the user is
on Google's consent screen*, so the callback pays cold start plus database resume at the
slowest possible moment. Password login is one request, and typing credentials often warms
things up first.

**Confirm it is not the application:**

```bash
curl -s -o /dev/null -w "%{time_total}s\n" \
  https://finme-backend.azurewebsites.net/api/auth/login -X POST \
  -H "Content-Type: application/json" -d '{"email":"x@y.z","password":"nope"}'
```

Warm, this returns a 401 in around 130 ms. If it does, the code is not the problem.

**Options:**

1. **Raise the auto-pause delay** — free. Moving from 15 to 60 minutes removes a large part of
   it.
2. **Keep the app warm with an external pinger** — free. Something like UptimeRobot every
   ~10 minutes. F1 allows 60 CPU-minutes per day, so keep the interval conservative; exhausting
   the quota stops the app entirely for the rest of the day.
3. **Move to B1** — about $17.75/month, enables Always On, removes cold start and the quota
   ceiling.

"Always On" is **not available on F1**. It is a platform restriction, not a setting.

## App Service returns 503 with QuotaExceeded

**Symptom.** The site is down. `az webapp show` reports `usageState: QuotaExceeded`.

**Cause.** The F1 daily CPU allowance (60 minutes) is spent. Repeated failed deploys are the
usual culprit — each one starts the app, which costs startup CPU whether or not it succeeds.

**Fix.** It resets daily. Fix the underlying failure before redeploying rather than retrying
into the same wall.

## Deployment starts then dies

Three distinct causes, each with its own log signature:

| Log line | Cause | Fix |
| --- | --- | --- |
| `database ... not currently available` | Serverless resuming | `initialization-fail-timeout=60000` |
| `Unable to determine Dialect without JDBC metadata` | Hibernate needs a live connection to detect it | Set the dialect explicitly |
| `Schema validation: missing table [bank_statements]` | `ddl-auto=validate` against an empty database | `ddl-auto=update` |

All three are covered in
[Environments](/operations/environments#production-settings-and-why-each-exists).

## Google sign-in fails with redirect_uri_mismatch

**Cause, most often.** The redirect URI registered in Google points at the frontend. It must
point at the **backend**: `https://finme-backend.azurewebsites.net/login/oauth2/code/google`.

**Cause, second most often.** `server.forward-headers-strategy=framework` is missing, so
Spring builds the URI from the internal `http://host:8080` request rather than the public
HTTPS host.

Google changes take minutes to a few hours to propagate. A failure immediately after saving is
not proof the configuration is wrong.

## Google sign-in says access blocked

The OAuth app is still in **Testing**, where only allow-listed emails can sign in. Publish it
— which requires a verified domain for the homepage, privacy and terms URLs.

## Statement upload reports no readable text

Working as intended. The PDF is a scan or a photo saved as a PDF, with no text layer to
extract. Download the real PDF from the banking app instead.

Without this check the statement would produce zero transactions and report `COMPLETE` — the
app claiming success for work it never did.

## Upload succeeds but nothing appears

A `202` means *accepted*, not *succeeded*. Extraction runs afterwards. Poll
`GET /api/statements/{id}` or `/api/receipts/{id}`; a `FAILED` status carries `failureReason`.

A client that stops at the upload response shows nothing and says nothing when extraction
fails.

## Recommendations are missing

Every AI provider failed or is rate-limited. The dashboard still shows correct totals — the
recommendations call is fetched separately and never awaited alongside the figures, precisely
so a provider outage cannot take the dashboard down.

The reason is displayed rather than the section vanishing.

## Deployed changes do not appear

**Frontend:** check Vercel built from `main`. A local commit that was never pushed is the
usual answer.

**Backend:** app settings changes have not always restarted the app here despite being
documented to. Run `az webapp restart` explicitly and verify the value is live:

```bash
az webapp config appsettings list --name finme-backend --resource-group finme-rg \
  --query "[?name=='CORS_ALLOWED_ORIGINS'].value" -o tsv
```

## Backend logs show almost nothing

File-based logging on App Service has proved unreliable here — capturing startup and then
little of the ongoing request traffic. Do not treat an empty log as evidence that nothing
happened. Reproduce with `curl` against the deployed API, where you see the real status code
and timing.
