import type { Page, Route } from "@playwright/test";

export const VIEWPORTS = {
  desktop: { width: 1440, height: 900 },
  laptop: { width: 1100, height: 800 },
  stackPoint: { width: 960, height: 800 },
  tablet: { width: 768, height: 1024 },
  phone: { width: 390, height: 844 },
  smallPhone: { width: 320, height: 640 },
} as const;

/**
 * The API origin the app is built against. Routes must be scoped to it rather than a bare
 * "**\/api/**" glob - that also matches Vite's own module URLs like /src/api/client.ts and
 * serves JSON in place of the app's JavaScript, which stops the page booting at all.
 */
export const API = "http://localhost:8080";

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

// Full Transaction shape, unlike the trimmed rows the calendar rail needs - the dashboard
// table renders sourceType, paymentMethod and status, and a mock missing them would be
// testing a contract the app does not actually have.
export const TRANSACTIONS = [
  {
    id: 1,
    sourceType: "STATEMENT",
    date: "2026-08-04",
    merchant: "Lovelysupermarket Zonnebloem",
    amount: 372.98,
    direction: "DEBIT",
    category: "Groceries",
    description: null,
    paymentMethod: "CARD",
    status: "CONFIRMED",
  },
  {
    id: 2,
    sourceType: "STATEMENT",
    date: "2026-08-03",
    merchant: "Pay Just Now Cape Town",
    amount: 352.66,
    direction: "DEBIT",
    category: "Entertainment",
    description: null,
    paymentMethod: "CARD",
    status: "CONFIRMED",
  },
  {
    id: 3,
    sourceType: "MANUAL",
    date: "2026-08-02",
    merchant: "Salary",
    amount: 18500.0,
    direction: "CREDIT",
    category: "Income",
    description: null,
    paymentMethod: "TRANSFER",
    status: "CONFIRMED",
  },
];

export const DASHBOARD_SUMMARY = {
  totalSpend: 725.64,
  categoryBreakdown: [
    { category: "Groceries", amount: 372.98 },
    { category: "Entertainment", amount: 352.66 },
  ],
  trend: [
    { month: "2026-06", amount: 1200.0 },
    { month: "2026-07", amount: 980.5 },
    { month: "2026-08", amount: 725.64 },
  ],
};

export const RECOMMENDATIONS = {
  recommendations: [
    "Groceries is your largest category this month at R372.98.",
    "You had 26 no-spend days in August.",
  ],
  unavailableReason: null,
};

export const BUDGETS = [
  {
    id: 1,
    category: "Groceries",
    monthlyLimit: 2000,
    spent: 372.98,
    remaining: 1627.02,
    percentUsed: 0.18649,
    overBudget: false,
  },
  // Deliberately over limit: the over-budget branch renders different copy and styling,
  // and a fixture of only healthy budgets would never reach it.
  {
    id: 2,
    category: "Entertainment",
    monthlyLimit: 300,
    spent: 352.66,
    remaining: -52.66,
    percentUsed: 1.17553,
    overBudget: true,
  },
];

export const CREDIT_PROFILE = {
  id: 1,
  bureau: "TransUnion",
  maxScore: 700,
  createdAt: "2026-08-01T10:00:00Z",
  currentScore: 612,
  scoreRecordedAt: "2026-08-15T10:00:00Z",
  accounts: [
    { id: 1, accountName: "Store Card", balance: 4750, creditLimit: 5000, paymentStatus: "ON_TIME" },
    { id: 2, accountName: "Credit Card", balance: 1200, creditLimit: 12000, paymentStatus: "ON_TIME" },
  ],
};

export const CREDIT_ANALYSIS = {
  overallUtilization: 0.35,
  totalBalance: 5950,
  totalLimit: 17000,
  accounts: [
    {
      accountId: 1,
      accountName: "Store Card",
      balance: 4750,
      creditLimit: 5000,
      utilization: 0.95,
      overallReduction: 0.2794,
    },
    {
      accountId: 2,
      accountName: "Credit Card",
      balance: 1200,
      creditLimit: 12000,
      utilization: 0.1,
      overallReduction: 0.0706,
    },
  ],
  plan: ["Clearing the Store Card would cut overall utilization most."],
  planUnavailableReason: null,
  // FR-2.3.3 - server-supplied constant, never model output. Must always render.
  disclaimer: "This is guidance, not financial advice.",
};

export const SCORE_COMPARISON = {
  currentScore: 612,
  previousScore: 598,
  previousRecordedAt: "2026-07-15T10:00:00Z",
};

/** Every GET the app makes, keyed by path prefix. Ordered specific-last on registration. */
const DEFAULT_GETS: Array<[string, unknown]> = [
  [`${API}/api/transactions**`, TRANSACTIONS],
  [`${API}/api/dashboard/summary**`, DASHBOARD_SUMMARY],
  [`${API}/api/dashboard/recommendations**`, RECOMMENDATIONS],
  [`${API}/api/dashboard/calendar**`, CALENDAR_FIXTURE],
  [`${API}/api/budgets**`, BUDGETS],
  [`${API}/api/credit/profile**`, CREDIT_PROFILE],
  [`${API}/api/credit/analysis**`, CREDIT_ANALYSIS],
  [`${API}/api/credit/score/comparison**`, SCORE_COMPARISON],
];

export interface MockOptions {
  /** Replace or add responses, keyed by the same glob used in DEFAULT_GETS. */
  overrides?: Array<[string, (route: Route) => void]>;
}

/**
 * Signs in without touching the backend and pins every API response to the fixtures above.
 *
 * Seeding localStorage directly is the point: these are layout and rendering tests, and
 * routing them through a real login would make them depend on a running backend, a real
 * credential and live data that changes underneath the assertions.
 */
export async function mockApi(page: Page, options: MockOptions = {}) {
  // Registered broadest-first: Playwright tries the most recently added route first, so
  // the catch-all has to go down before the specific ones or it shadows them.
  await page.route(`${API}/api/**`, (route) => route.fulfill({ json: [] }));

  for (const [glob, json] of DEFAULT_GETS) {
    await page.route(glob, (route) => {
      // Only GETs are canned - a POST/PUT/DELETE hitting one of these paths falls through
      // to an empty 200 so tests must opt in to mutation behaviour explicitly.
      if (route.request().method() !== "GET") return route.fulfill({ json: {} });
      return route.fulfill({ json });
    });
  }

  for (const [glob, handler] of options.overrides ?? []) {
    await page.route(glob, handler);
  }

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
}

/** Mocks the API, signs in, opens `path`, and waits for the page shell to render. */
export async function gotoAuthenticated(page: Page, path: string, options: MockOptions = {}) {
  await mockApi(page, options);
  await page.goto(path);
  await page.waitForSelector("main");
}

export async function gotoCalendarAuthenticated(page: Page, options: MockOptions = {}) {
  // The calendar queries /api/transactions?from=&to= for a single day, so it gets the
  // day-scoped rows rather than the dashboard's whole-month list. Prepended, so a test's
  // own override still wins.
  await gotoAuthenticated(page, "/calendar", {
    ...options,
    overrides: [
      [`${API}/api/transactions**`, (route: Route) => route.fulfill({ json: DAY_TRANSACTIONS })],
      ...(options.overrides ?? []),
    ],
  });
  await page.waitForSelector(".calendar-grid");
}

/**
 * Clicks a day by its number.
 *
 * Matching on the rendered amount instead looks tempting and is a trap: the squares round
 * with toFixed(0), so R745.64 renders "R746" and a hasText:"745" filter silently matches
 * nothing. The day number is the stable handle.
 */
export async function clickDay(page: Page, dayNumber: number) {
  await page
    .locator(".calendar-day:not(.calendar-day--blank)")
    .filter({ has: page.locator(`.calendar-day-number:text-is("${dayNumber}")`) })
    .click();
}

/**
 * Fails if page content sticks out past the viewport - the actual definition of "broken
 * on mobile".
 *
 * Scoped to <main> deliberately. The closed mobile nav drawer is parked off-screen to the
 * right by design and is site-wide, so a document-level check reports it on every page and
 * would say nothing about the page under test.
 */
export async function hasHorizontalOverflow(page: Page): Promise<boolean> {
  return page.evaluate(() => {
    const limit = document.documentElement.clientWidth + 1;
    const main = document.querySelector("main");
    if (!main) return true;
    return [main, ...main.querySelectorAll("*")].some(
      (el) => el.getBoundingClientRect().right > limit
    );
  });
}

/** Names the widest offending element, so a failure says what to fix rather than just "true". */
export async function describeOverflow(page: Page): Promise<string> {
  return page.evaluate(() => {
    const limit = document.documentElement.clientWidth + 1;
    const main = document.querySelector("main");
    if (!main) return "no <main> element";
    const worst = [main, ...main.querySelectorAll("*")]
      .map((el) => ({ el, right: el.getBoundingClientRect().right }))
      .filter((x) => x.right > limit)
      .sort((a, b) => b.right - a.right)[0];
    if (!worst) return "none";
    const el = worst.el as HTMLElement;
    return `<${el.tagName.toLowerCase()} class="${el.className}"> extends to ${worst.right.toFixed(0)}px (limit ${limit}px)`;
  });
}
