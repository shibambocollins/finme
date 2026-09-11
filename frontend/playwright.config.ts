import { defineConfig, devices } from "@playwright/test";

// Layout tests need a real engine - jsdom reports every element as 0x0, so it cannot tell
// whether the calendar overflows or a day cell has collapsed. Chromium only: these assert
// layout maths that does not differ across engines, and one browser keeps CI cheap.
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? "github" : [["list"]],
  use: {
    baseURL: "http://localhost:5173",
    trace: "on-first-retry",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  // Every /api/** call is intercepted in the tests, so this never needs a backend running.
  webServer: {
    command: "npm run dev",
    url: "http://localhost:5173",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
