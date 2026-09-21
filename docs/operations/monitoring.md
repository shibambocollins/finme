# Monitoring

An honest account: monitoring on this deployment is thin, and the gaps are known rather than
unnoticed.

## What exists

### Application logs

```bash
# live
az webapp log tail --name finme-backend --resource-group finme-rg

# download
az webapp log download --name finme-backend --resource-group finme-rg --log-file logs.zip
```

::: warning File logging here is not trustworthy
It has captured startup and then little of the ongoing request traffic. An empty log is not
evidence that nothing happened. Reproduce against the deployed API with `curl` when you need
certainty about a status code or timing.
:::

### Health

```bash
az webapp show --name finme-backend --resource-group finme-rg \
  --query "{state:state, availability:availabilityState, usage:usageState}"
```

`usageState: QuotaExceeded` means the F1 daily CPU allowance is spent and the app is down
until it resets.

### Vercel

Build logs, deployment history and one-click rollback in the dashboard. Analytics are
available on the Hobby tier.

### CI

GitHub Actions on every push and PR. A red `main` is the earliest signal that something is
wrong, and in practice the most reliable one.

### Cost

```bash
az consumption usage list --query "[].{name:instanceName, cost:pretaxCost}" -o table
```

Student credit balance is **not** retrievable from the CLI — the Consumption balances API
returns `InvalidResourceType` for Azure for Students subscriptions. Use the portal.

## What the application reports about itself

**Ingestion outcomes are queryable.** Every statement and receipt row carries a status and, on
failure, a `failureReason`. This is the most useful operational data the system holds:

```sql
SELECT status, failure_reason, COUNT(*)
FROM bank_statements
GROUP BY status, failure_reason;
```

Rising `FAILED` counts with AI-provider reasons mean the chain is struggling. Rising
`No readable text` means users are uploading scans, which is a documentation problem rather
than a system one.

**Provider fallback is logged.** Each fall-through from one provider to the next is logged, so
the logs show whether the primary is healthy — when the logs are working.

## Gaps

Worth naming rather than implying coverage that does not exist:

| Gap | Consequence |
| --- | --- |
| No uptime monitoring | Nobody is told when the app is down |
| No error aggregation | No Sentry or equivalent; failures are found by reading logs |
| No metrics | No request rate, latency or error-rate series |
| No alerting | No notification on anything |
| No structured logging | Plain text, so logs cannot be queried usefully |
| Unreliable log capture | The one tool that does exist cannot be fully trusted |

The honest summary: **failures are currently found by someone using the app**.

## What to add first

In order of value for the effort:

1. **Uptime monitoring** — free, and doubles as the keep-warm pinger that fixes cold starts
   ([Troubleshooting](/operations/troubleshooting#slow-first-request)). Highest value by a
   distance.
2. **Error aggregation** — Sentry's free tier covers both frontend and backend, and removes
   the dependence on unreliable log files.
3. **Application Insights** — native to App Service, gives request metrics and a real log
   pipeline. Watch the cost on a student subscription.
4. **Structured JSON logging** — makes everything above more useful.

## Manual checks

Until the above exists, these are the checks worth running after any deployment:

```bash
# is it up
curl -s -o /dev/null -w "%{http_code} %{time_total}s\n" https://finme.me

# is the API answering, and warm
curl -s -o /dev/null -w "%{http_code} %{time_total}s\n" \
  https://finme-backend.azurewebsites.net/api/auth/login -X POST \
  -H "Content-Type: application/json" -d '{"email":"x@y.z","password":"nope"}'

# is CORS correct for the live origin
curl -s -i -X OPTIONS "https://finme-backend.azurewebsites.net/api/dashboard/summary" \
  -H "Origin: https://finme.me" -H "Access-Control-Request-Method: GET" \
  | grep -i "^access-control"
```

Expect `401` in around 130 ms from the second when warm. The third must return an
`Access-Control-Allow-Origin` matching the origin — a `200` alone proves nothing.
