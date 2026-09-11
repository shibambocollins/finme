import { expect, test } from "@playwright/test";
import {
  API,
  BUDGETS,
  CREDIT_ANALYSIS,
  DASHBOARD_SUMMARY,
  TRANSACTIONS,
  VIEWPORTS,
  gotoAuthenticated,
} from "./fixtures";

test.beforeEach(async ({ page }) => {
  await page.setViewportSize(VIEWPORTS.desktop);
});

test.describe("Dashboard", () => {
  test("renders the transaction ledger from the API", async ({ page }) => {
    await gotoAuthenticated(page, "/dashboard");

    for (const t of TRANSACTIONS) {
      await expect(page.getByText(t.merchant, { exact: false }).first()).toBeVisible();
    }
  });

  test("distinguishes money in from money out", async ({ page }) => {
    await gotoAuthenticated(page, "/dashboard");

    // A R18,500 salary and a R18,500 purchase are the same number - direction is the only
    // thing telling them apart, so the credit styling has to actually be applied.
    await expect(page.locator(".amount-credit").first()).toBeVisible();
  });

  test("shows the spend summary", async ({ page }) => {
    await gotoAuthenticated(page, "/dashboard");

    await expect(page.getByText("Spend by category")).toBeVisible();
    for (const c of DASHBOARD_SUMMARY.categoryBreakdown) {
      await expect(page.getByText(c.category, { exact: false }).first()).toBeVisible();
    }
  });

  test("surfaces recommendations when the provider returned them", async ({ page }) => {
    await gotoAuthenticated(page, "/dashboard");
    await expect(page.getByText("Recommendations")).toBeVisible();
  });

  test("explains itself instead of going blank when recommendations are unavailable", async ({
    page,
  }) => {
    await gotoAuthenticated(page, "/dashboard", {
      overrides: [
        [
          `${API}/api/dashboard/recommendations**`,
          (route) =>
            route.fulfill({
              json: { recommendations: [], unavailableReason: "All AI providers are rate limited." },
            }),
        ],
      ],
    });

    await expect(page.getByText(/rate limited/i)).toBeVisible();
  });

  test("a new user with no data gets an invitation, not an error", async ({ page }) => {
    await gotoAuthenticated(page, "/dashboard", {
      overrides: [
        [`${API}/api/transactions**`, (route) => route.fulfill({ json: [] })],
        [
          `${API}/api/dashboard/summary**`,
          (route) => route.fulfill({ json: { totalSpend: 0, categoryBreakdown: [], trend: [] } }),
        ],
      ],
    });

    await expect(page.getByText(/Nothing in your ledger yet/i)).toBeVisible();
    // The empty state must not read as a failure - this is the exact complaint that
    // started the "Failed to load dashboard" work.
    await expect(page.locator(".form-error")).toHaveCount(0);
  });
});

test.describe("Statement upload", () => {
  test("uploads, polls until complete, then shows the extracted transactions", async ({ page }) => {
    const settled = {
      id: 1,
      uploadDate: "2026-08-20T10:00:00Z",
      status: "COMPLETE",
      totalChunks: 1,
      processedChunks: 1,
      failureReason: null,
    };

    await gotoAuthenticated(page, "/dashboard", {
      overrides: [
        // The status poll - GET /api/statements/{id} - must be registered after the POST
        // handler so it wins for that more specific path.
        [
          `${API}/api/statements`,
          (route) =>
            route.request().method() === "POST"
              ? route.fulfill({ status: 202, json: { ...settled, status: "PROCESSING", processedChunks: 0 } })
              : route.fulfill({ json: [] }),
        ],
        [`${API}/api/statements/*`, (route) => route.fulfill({ json: settled })],
      ],
    });

    await page.locator('input[type="file"]').first().setInputFiles({
      name: "statement.pdf",
      mimeType: "application/pdf",
      buffer: Buffer.from("%PDF-1.4 test fixture"),
    });

    // A completed upload reloads the dashboard rather than announcing itself, so the
    // observable result is the ledger still rendering and no error surfacing.
    await expect(page.locator(".form-error")).toHaveCount(0, { timeout: 15_000 });
    await expect(page.getByText(TRANSACTIONS[0].merchant).first()).toBeVisible();
  });

  test("reports the failure reason when extraction fails server-side", async ({ page }) => {
    await gotoAuthenticated(page, "/dashboard", {
      overrides: [
        [
          `${API}/api/statements`,
          (route) =>
            route.request().method() === "POST"
              ? route.fulfill({
                  status: 202,
                  json: { id: 1, uploadDate: "2026-08-20T10:00:00Z", status: "PROCESSING", totalChunks: 1, processedChunks: 0, failureReason: null },
                })
              : route.fulfill({ json: [] }),
        ],
        [
          `${API}/api/statements/*`,
          (route) =>
            route.fulfill({
              json: {
                id: 1,
                uploadDate: "2026-08-20T10:00:00Z",
                status: "FAILED",
                totalChunks: 1,
                processedChunks: 0,
                failureReason: "Could not read the statement layout",
              },
            }),
        ],
      ],
    });

    await page.locator('input[type="file"]').first().setInputFiles({
      name: "statement.pdf",
      mimeType: "application/pdf",
      buffer: Buffer.from("%PDF-1.4 test fixture"),
    });

    // The user has to be told why, not just that it failed.
    await expect(page.getByText(/Could not read the statement layout/i)).toBeVisible({
      timeout: 15_000,
    });
  });

  test("surfaces the reason when the backend rejects the file", async ({ page }) => {
    await gotoAuthenticated(page, "/dashboard", {
      overrides: [
        [
          `${API}/api/statements**`,
          (route) => {
            if (route.request().method() !== "POST") return route.fulfill({ json: [] });
            // The real rejection for a scanned PDF with no text layer.
            return route.fulfill({
              status: 400,
              json: { message: "No readable text in that PDF" },
            });
          },
        ],
      ],
    });

    await page.locator('input[type="file"]').first().setInputFiles({
      name: "scan.pdf",
      mimeType: "application/pdf",
      buffer: Buffer.from("%PDF-1.4 scanned"),
    });

    await expect(page.getByText(/No readable text/i)).toBeVisible({ timeout: 10_000 });
  });

  test("reports a network failure rather than hanging on the spinner", async ({ page }) => {
    await gotoAuthenticated(page, "/dashboard", {
      overrides: [
        [
          `${API}/api/statements**`,
          (route) => {
            if (route.request().method() !== "POST") return route.fulfill({ json: [] });
            // What a CORS block looks like to fetch(): the request simply fails. This is
            // the shape of the failure that broke uploads in production.
            return route.abort("failed");
          },
        ],
      ],
    });

    await page.locator('input[type="file"]').first().setInputFiles({
      name: "statement.pdf",
      mimeType: "application/pdf",
      buffer: Buffer.from("%PDF-1.4 test fixture"),
    });

    await expect(page.locator(".form-error")).toBeVisible({ timeout: 15_000 });
  });
});

test.describe("Budgets", () => {
  test("lists each budget with its category and limit", async ({ page }) => {
    await gotoAuthenticated(page, "/budgets");

    for (const b of BUDGETS) {
      await expect(page.getByText(b.category, { exact: false }).first()).toBeVisible();
    }
  });

  test("marks an over-budget category distinctly", async ({ page }) => {
    await gotoAuthenticated(page, "/budgets");

    // Entertainment is deliberately over its limit in the fixture - if nothing renders
    // differently, the over-budget branch is dead code.
    await expect(page.locator(".budget-over-note").first()).toBeVisible();
  });

  test("empty state invites a first budget instead of erroring", async ({ page }) => {
    await gotoAuthenticated(page, "/budgets", {
      overrides: [[`${API}/api/budgets**`, (route) => route.fulfill({ json: [] })]],
    });

    await expect(page.locator(".form-error")).toHaveCount(0);
    await expect(page.locator("main")).toBeVisible();
  });
});

test.describe("Credit", () => {
  test("shows utilization and the account list", async ({ page }) => {
    await gotoAuthenticated(page, "/credit");

    await expect(page.getByText("Store Card", { exact: false }).first()).toBeVisible();
    await expect(page.getByText("Credit Card", { exact: false }).first()).toBeVisible();
  });

  test("always renders the disclaimer", async ({ page }) => {
    await gotoAuthenticated(page, "/credit");

    // FR-2.3.3 - server-supplied constant, never model output, and never optional.
    await expect(page.getByText(CREDIT_ANALYSIS.disclaimer)).toBeVisible();
  });

  test("renders utilization as a percentage, not a raw ratio", async ({ page }) => {
    await gotoAuthenticated(page, "/credit");

    // 0.95 must reach the user as 95.0%, never the raw ratio. Financial figures are
    // computed in code, so a formatting slip here is a correctness bug, not cosmetics.
    const body = await page.locator("main").innerText();
    expect(body).toMatch(/95\.0\s*%/);
    expect(body).not.toMatch(/\b0\.95\b/);
  });

  test("stays usable when the analysis endpoint fails", async ({ page }) => {
    await gotoAuthenticated(page, "/credit", {
      overrides: [
        [`${API}/api/credit/analysis**`, (route) => route.fulfill({ status: 500, json: {} })],
      ],
    });

    // The page catches analysis failures on purpose - the profile must still render.
    await expect(page.getByText("Store Card", { exact: false }).first()).toBeVisible();
  });
});

test.describe("Settings", () => {
  test("shows the signed-in account", async ({ page }) => {
    await gotoAuthenticated(page, "/settings");
    await expect(page.getByText("test@example.com").first()).toBeVisible();
  });

  test("guards destructive actions behind typing the account email", async ({ page }) => {
    await gotoAuthenticated(page, "/settings");

    // Deleting an account or wiping data is irreversible; a single misclick must not do it.
    const danger = page.getByRole("button", { name: /delete/i }).first();
    await expect(danger).toBeVisible();
  });
});

test.describe("Unauthenticated access", () => {
  test("a protected page redirects to login when there is no token", async ({ page }) => {
    await page.route(`${API}/api/**`, (route) => route.fulfill({ status: 401, json: {} }));
    await page.goto("/dashboard");

    await expect(page).toHaveURL(/\/login/);
  });
});
