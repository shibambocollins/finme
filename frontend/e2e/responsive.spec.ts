import { expect, test } from "@playwright/test";
import {
  VIEWPORTS,
  describeOverflow,
  gotoAuthenticated,
  hasHorizontalOverflow,
} from "./fixtures";

/**
 * The layout contract every signed-in page has to meet at every width.
 *
 * This exists because the calendar's phone layout was broken for a while without anyone
 * noticing - the media queries were being overridden and nothing said so. One overflow
 * regression on any page is the same class of bug.
 */
const PAGES = [
  { name: "Dashboard", path: "/dashboard" },
  { name: "Calendar", path: "/calendar" },
  { name: "Budgets", path: "/budgets" },
  { name: "Credit", path: "/credit" },
  { name: "Settings", path: "/settings" },
];

for (const { name, path } of PAGES) {
  test.describe(`${name} layout`, () => {
    for (const [label, viewport] of Object.entries(VIEWPORTS)) {
      test(`${label} (${viewport.width}px): content stays inside the viewport`, async ({ page }) => {
        await page.setViewportSize(viewport);
        await gotoAuthenticated(page, path);

        const overflowed = await hasHorizontalOverflow(page);
        // Report the offending element - "expected false, got true" alone gives whoever
        // hits this nothing to act on.
        expect(overflowed, await describeOverflow(page)).toBe(false);
      });
    }

    test("phone: primary heading renders inside the screen", async ({ page }) => {
      await page.setViewportSize(VIEWPORTS.phone);
      await gotoAuthenticated(page, path);

      // h1 or h2: Dashboard currently starts at h2 with no h1 at all. That is worth
      // fixing for accessibility, but it is a document-outline issue rather than a
      // layout one, so this asserts what every page does have.
      const heading = page.locator("main :is(h1, h2)").first();
      await expect(heading).toBeVisible();
      const box = await heading.boundingBox();
      expect(box!.width).toBeLessThanOrEqual(VIEWPORTS.phone.width);
    });

    test("phone: no element forces a horizontal scrollbar on the document", async ({ page }) => {
      await page.setViewportSize(VIEWPORTS.phone);
      await gotoAuthenticated(page, path);

      // The <main> check above ignores the off-screen nav drawer by design; this one
      // confirms the page as a whole still does not scroll sideways.
      const scrollable = await page.evaluate(() => {
        const de = document.documentElement;
        return de.scrollWidth > de.clientWidth + 1;
      });
      expect(scrollable).toBe(false);
    });
  });
}

test.describe("Mobile navigation", () => {
  test("the drawer is hidden until opened, then lists every section", async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.phone);
    await gotoAuthenticated(page, "/dashboard");

    const drawer = page.locator(".app-header__drawer");
    await expect(drawer).not.toHaveClass(/open/);

    await page.locator(".app-header__menu-btn").click();
    await expect(drawer).toHaveClass(/open/);

    for (const label of ["Dashboard", "Calendar", "Budgets", "Credit"]) {
      await expect(drawer.getByRole("link", { name: label })).toBeVisible();
    }
  });

  test("desktop shows inline nav rather than the menu button", async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.desktop);
    await gotoAuthenticated(page, "/dashboard");

    await expect(page.locator(".app-header__nav--desktop")).toBeVisible();
    await expect(page.locator(".app-header__menu-btn")).toBeHidden();
  });
});
