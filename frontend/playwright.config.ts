import { defineConfig, devices } from "@playwright/test";

/**
 * E2E config for the redesigned UI. Runs against a `pnpm dev` instance already listening on
 * :3000 (started separately, not via `webServer`, since a full run also needs the gateway +
 * backend services up — see frontend/CLAUDE.md and root CLAUDE.md's port table).
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 30_000,
  expect: { timeout: 8_000 },
  fullyParallel: false,
  retries: 0,
  reporter: [["list"]],
  use: {
    baseURL: "http://localhost:3000",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
