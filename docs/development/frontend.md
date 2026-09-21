# Frontend

React 19, TypeScript, Vite. `frontend/`.

## Layout

```text
src/
├── api/          Fetch wrapper and error type
├── auth/         Auth context, JWT decoding, route guard
├── components/   Shared components
├── constants/    Category suggestions
├── hooks/        Document title
├── pages/        One file per route
├── utils/        Date formatting, CSV export
├── App.css       All application styling
└── index.css     Design tokens
```

## Routes

| Path | Page | Guarded |
| --- | --- | --- |
| `/` | Landing | — |
| `/register`, `/login` | Auth | — |
| `/auth-callback` | OAuth and email-verification landing | — |
| `/privacy`, `/terms` | Legal | — |
| `/dashboard` | Ledger, upload, charts | ✅ |
| `/calendar` | Day-by-day spend | ✅ |
| `/budgets` | Category budgets | ✅ |
| `/credit` | Credit profile and analysis | ✅ |
| `/settings` | Profile, data deletion | ✅ |

## API access

One wrapper in `api/client.ts` — `apiGet`, `apiPostJson`, `apiPostForm`, `apiPut`,
`apiDelete`. Each attaches the bearer token and throws a typed `ApiError` carrying the status
and the server's message, so pages surface the real reason rather than a generic failure.

Base URL comes from `VITE_API_BASE_URL`.

## Auth state

`AuthContext` holds the token, email and display name, persisted to `localStorage` under
`finme.auth`. `completeOAuthLogin` handles the callback path, where the token arrives in the
query string rather than a response body.

`/auth-callback` serves both Google login and email verification — both end in "the backend
redirects here with a token". They diverge on one thing: only the verification link carries
`&verified=true`, which is what shows a confirmation instead of continuing straight through.

## Polling uploads

Both uploads return `202` and are polled until settled:

```typescript
const accepted = await apiPostForm<BankStatementResponse>("/api/statements", form, token);
const settled = await pollUntilSettled(accepted.id);
if (settled.status === "FAILED") {
  setError(settled.failureReason ?? "Statement processing failed");
}
```

Statements poll with a 20-minute deadline and report a percentage; receipts use 3 minutes and
report no progress, since a receipt is one vision call rather than a chunked document.

Progress is shown as a percentage, never "part 3 of 7" — the chunk count is how the backend
paces around a rate limit, not something a user should have to understand.

## Formatting

`utils/dates.ts` is the single place dates are formatted, and it is hand-built rather than
delegating to `toLocaleDateString`:

- With no locale argument, the order follows the *viewer's* OS. A US-configured browser
  renders 4 August as `08/04/2026` — wrong, and wrong invisibly.
- `en-ZA` in Chromium produces `2026/08/04`, which is not what South Africans write.

So day-first is spelled out explicitly. Dates are also parsed from parts, because
`new Date("2026-08-04")` is treated as UTC midnight and renders as the previous day in any
timezone behind UTC.

CSV export deliberately keeps ISO: it sorts correctly as text, where `04/08/2026` is read as
8 April by a spreadsheet set to a US locale. Display format and interchange format are
different decisions.

## Styling

Plain CSS with tokens in `index.css`. No framework, no CSS-in-JS.

Rules worth knowing:

- **Media queries go at the end of their section.** Equal specificity means source order
  decides, so a media block placed above the base rules it overrides silently loses. This
  happened — the entire phone layout of the calendar was inert for a while.
- **`minmax(0, 1fr)`, not `1fr`,** for grid columns that must shrink. Plain `1fr` floors each
  column at its content width, which overflowed the calendar on every phone.

## Testing

101 Playwright tests in `e2e/`, real Chromium, every API call stubbed — no backend needed.

| File | Covers |
| --- | --- |
| `fixtures.ts` | Mock data and auth bypass |
| `responsive.spec.ts` | Overflow and legibility, 6 viewports × 5 pages |
| `pages.spec.ts` | Behaviour per page |
| `calendar-responsive.spec.ts` | Calendar layout and summary arithmetic |

jsdom was rejected: it reports every element as 0×0, so it cannot detect overflow — the exact
class of bug this suite exists to catch. The suite has found real defects, including a
StrictMode double-invoke that greeted every new user with "welcome back".

```bash
npm run test:e2e        # headless
npm run test:e2e:ui     # interactive
```
