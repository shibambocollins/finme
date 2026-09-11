import { expect, test } from "@playwright/test";
import {
  EXPECTED,
  VIEWPORTS,
  clickDay,
  gotoCalendarAuthenticated,
  hasHorizontalOverflow,
} from "./fixtures";

test.describe("Calendar layout across screen sizes", () => {
  for (const [name, viewport] of Object.entries(VIEWPORTS)) {
    test(`${name} (${viewport.width}px): no horizontal overflow`, async ({ page }) => {
      await page.setViewportSize(viewport);
      await gotoCalendarAuthenticated(page);
      expect(await hasHorizontalOverflow(page)).toBe(false);
    });

    test(`${name} (${viewport.width}px): day cells stay readable`, async ({ page }) => {
      await page.setViewportSize(viewport);
      await gotoCalendarAuthenticated(page);

      const cell = page.locator(".calendar-day:not(.calendar-day--blank)").first();
      const box = await cell.boundingBox();
      expect(box).not.toBeNull();
      // 38px was the floor the layout maths was checked against down to a 320px viewport.
      expect(box!.width).toBeGreaterThanOrEqual(38);
      expect(box!.height).toBeGreaterThanOrEqual(40);
    });

    test(`${name} (${viewport.width}px): all seven weekday columns are present`, async ({ page }) => {
      await page.setViewportSize(viewport);
      await gotoCalendarAuthenticated(page);
      await expect(page.locator(".calendar-weekday")).toHaveCount(7);
    });
  }

  test("wide screens put the summary beside the grid, not below it", async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.desktop);
    await gotoCalendarAuthenticated(page);

    const grid = await page.locator(".calendar-grid").boundingBox();
    const side = await page.locator(".calendar-side").boundingBox();
    expect(grid).not.toBeNull();
    expect(side).not.toBeNull();

    // The rail starts after the grid ends horizontally, and their vertical ranges overlap
    // - which is what "beside" means and what "stacked below" would fail.
    expect(side!.x).toBeGreaterThan(grid!.x + grid!.width - 1);
    expect(side!.y).toBeLessThan(grid!.y + grid!.height);
  });

  test("the summary rail stays narrower than the calendar it sits next to", async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.desktop);
    await gotoCalendarAuthenticated(page);

    const grid = await page.locator(".calendar-grid").boundingBox();
    const side = await page.locator(".calendar-side").boundingBox();
    // The calendar has to stay the thing you look at - a rail that grows past the grid
    // would invert that.
    expect(side!.width).toBeLessThan(grid!.width);
  });

  test("narrow screens stack the summary below the grid", async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.phone);
    await gotoCalendarAuthenticated(page);

    const grid = await page.locator(".calendar-grid").boundingBox();
    const side = await page.locator(".calendar-side").boundingBox();
    expect(side!.y).toBeGreaterThanOrEqual(grid!.y + grid!.height - 1);
  });

  test("the month summary is visible without scrolling on a laptop", async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.laptop);
    await gotoCalendarAuthenticated(page);

    // The whole point of the redesign: the summary used to sit below the fold where it
    // was reliably missed.
    const total = page.getByText("Total spent");
    await expect(total).toBeInViewport();
  });
});

test.describe("Calendar summary figures", () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.desktop);
    await gotoCalendarAuthenticated(page);
  });

  test("month total matches the sum of the day squares", async ({ page }) => {
    const panel = page.locator(".calendar-panel").first();
    await expect(panel).toContainText(`R${EXPECTED.total.toFixed(2)}`);
  });

  test("spending and no-spend day counts add up to the month length", async ({ page }) => {
    const panel = page.locator(".calendar-panel").first();
    await expect(panel).toContainText(String(EXPECTED.spentDays));
    await expect(panel).toContainText(String(EXPECTED.zeroDays));
    expect(EXPECTED.spentDays + EXPECTED.zeroDays).toBe(EXPECTED.dayCount);
  });

  test("average divides by every day in the month, not just active ones", async ({ page }) => {
    const perCalendarDay = EXPECTED.total / EXPECTED.dayCount;
    const perActiveDay = EXPECTED.total / EXPECTED.spentDays;
    const panel = page.locator(".calendar-panel").first();

    await expect(panel).toContainText(`R${perCalendarDay.toFixed(2)}`);
    // Guards the exact regression this replaced - the two divisors differ enough here
    // that showing the wrong one cannot pass by coincidence.
    await expect(panel).not.toContainText(`R${perActiveDay.toFixed(2)}`);
  });

  test("busiest day reports the heaviest square", async ({ page }) => {
    const panel = page.locator(".calendar-panel").first();
    await expect(panel).toContainText(`${EXPECTED.busiest.day} - R${EXPECTED.busiest.total.toFixed(2)}`);
  });
});

test.describe("Day detail and breakdown", () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.desktop);
    await gotoCalendarAuthenticated(page);
  });

  test("prompts for a selection before a day is picked", async ({ page }) => {
    await expect(page.getByText("Select a day to see what was spent.")).toBeVisible();
  });

  test("clicking a day lists that day's transactions", async ({ page }) => {
    await clickDay(page, 4);
    await expect(page.locator(".calendar-day-list li")).toHaveCount(4);
    await expect(page.getByText("Lovelysupermarket Zonnebloem")).toBeVisible();
  });

  test("merchant and amount share one row instead of stacking", async ({ page }) => {
    await clickDay(page, 4);

    const row = page.locator(".calendar-day-list li").first();
    const merchant = await row.locator(".calendar-day-list-merchant").boundingBox();
    const amount = await row.locator("span").last().boundingBox();

    // The bug this replaced: the amount fell onto its own line. Same row means their
    // vertical centres line up and the amount sits to the right.
    expect(Math.abs((merchant!.y + merchant!.height / 2) - (amount!.y + amount!.height / 2)))
      .toBeLessThan(merchant!.height);
    expect(amount!.x).toBeGreaterThan(merchant!.x);
  });

  test("long merchant names do not wrap past two lines in the rail", async ({ page }) => {
    await clickDay(page, 4);

    const merchant = page.locator(".calendar-day-list-merchant").first();
    const box = await merchant.boundingBox();
    const lineHeight = await merchant.evaluate(
      (el) => parseFloat(getComputedStyle(el).lineHeight) || parseFloat(getComputedStyle(el).fontSize) * 1.35
    );
    // The rail was widened precisely because these ran to three lines at 260px.
    expect(box!.height).toBeLessThanOrEqual(lineHeight * 2 + 2);
  });

  test("breakdown totals only the debits, excluding income", async ({ page }) => {
    await clickDay(page, 4);

    const breakdown = page.locator(".calendar-panel", { hasText: "Where it went" });
    await expect(breakdown.locator(".calendar-breakdown-total")).toHaveText(
      `R${EXPECTED.dayDebitTotal.toFixed(2)}`
    );
    // Mixing money in with money out would make the shares meaningless.
    await expect(breakdown).not.toContainText("Income");
  });

  test("breakdown sorts categories biggest first", async ({ page }) => {
    await clickDay(page, 4);

    const amounts = await page.locator(".calendar-breakdown-amount").allTextContents();
    const values = amounts.map((t) => parseFloat(t.replace(/[^\d.]/g, "")));
    expect(values).toEqual([...values].sort((a, b) => b - a));
  });

  test("a zero-spend day says so rather than looking like missing data", async ({ page }) => {
    await page.route("http://localhost:8080/api/transactions**", (route) =>
      route.fulfill({ json: [] })
    );
    await clickDay(page, 20);

    await expect(page.getByText(/zero-spend day is a real result/)).toBeVisible();
    await expect(page.locator(".calendar-panel", { hasText: "Where it went" })).toHaveCount(0);
  });
});
