import type { Page } from "@playwright/test";

export const VIEWPORTS = {
  desktop: { width: 1440, height: 900 },
  laptop: { width: 1100, height: 800 },
  stackPoint: { width: 960, height: 800 },
  tablet: { width: 768, height: 1024 },
  phone: { width: 390, height: 844 },
  smallPhone: { width: 320, height: 640 },
} as const;

// August 2026 with a deliberate spread: a heavy day, a light day, and zero-spend days,
// so the summary maths is checked against something with an actual answer rather than a
// uniform month where a wrong divisor would still look plausible.
const CALENDAR_DAYS = [
  { date: "2026-08-01", total: 444.2 },
  { date: "2026-08-02", total: 254.1 },
  { date: "2026-08-03", total: 521.0 },
  { date: "2026-08-04", total: 745.64 },
  { date: "2026-08-05", total: 87.0 },
  ...Array.from({ length: 26 }, (_, i) => ({
    date: `2026-08-${String(i + 6).padStart(2, "0")}`,
    total: 0,
  })),
];

export const CALENDAR_FIXTURE = {
  month: "2026-08",
  days: CALENDAR_DAYS,
};

// Long merchant names on purpose - these are what wrapped to three lines in the narrow
// rail and prompted the widening, so the tests must keep exercising that case.
export const DAY_TRANSACTIONS = [
  { id: 1, merchant: "Lovelysupermarket Zonnebloem", amount: 372.98, direction: "DEBIT", category: "Groceries" },
  { id: 2, merchant: "Pay Just Now Cape Town", amount: 352.66, direction: "DEBIT", category: "Entertainment" },
  { id: 3, merchant: "Live Better Round-up Transfer", amount: 20.0, direction: "DEBIT", category: "Other" },
  { id: 4, merchant: "T Nocuze", amount: 20.0, direction: "CREDIT", category: "Income" },
];

export const EXPECTED = {
  total: CALENDAR_DAYS.reduce((sum, d) => sum + d.total, 0),
  dayCount: CALENDAR_DAYS.length,
  spentDays: CALENDAR_DAYS.filter((d) => d.total > 0).length,
  zeroDays: CALENDAR_DAYS.filter((d) => d.total === 0).length,
  busiest: { day: 4, total: 745.64 },
  // Only DEBIT rows - credits are excluded from the breakdown on purpose.
  dayDebitTotal: DAY_TRANSACTIONS.filter((t) => t.direction === "DEBIT").reduce((s, t) => s + t.amount, 0),
};

/**
 * Signs in without touching the backend and pins every /api/** response to the fixtures
 * above. Seeding localStorage directly is the point: these are layout tests, and routing
 * them through a real login would make them depend on a running backend and live data.
 */
export async function gotoCalendarAuthenticated(page: Page) {
  // Scoped to the API origin, not a bare "**/api/**" - that also matches Vite's own
  // module URLs like /src/api/client.ts and serves JSON in place of the app's JavaScript,
  // which stops the page booting at all.
  const api = "http://localhost:8080";

  await page.route(`${api}/api/dashboard/calendar**`, (route) =>
    route.fulfill({ json: CALENDAR_FIXTURE })
  );
  await page.route(`${api}/api/transactions**`, (route) =>
    route.fulfill({ json: DAY_TRANSACTIONS })
  );
  // Anything else the page reaches for resolves empty rather than hanging on a real host.
  await page.route(`${api}/api/**`, (route) => route.fulfill({ json: [] }));

  await page.addInitScript(() => {
    localStorage.setItem(
      "finme.auth",
      JSON.stringify({
        token: "test-token-not-a-real-credential",
        email: "test@example.com",
        displayName: "Collins Shibambo",
      })
    );
  });

  await page.goto("/calendar");
  await page.waitForSelector(".calendar-grid");
}

/** Fails if an element sticks out past the viewport - the actual definition of "broken on mobile". */
export async function hasHorizontalOverflow(page: Page): Promise<boolean> {
  return page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1
  );
}
