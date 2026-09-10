import { test, expect } from "@playwright/test";

/**
 * Verifies the "control panel" redesign actually renders — dark surface, the new type stack, and
 * no console/page errors — on every route that's reachable without a live session. This is the
 * regression guard for the UI restyle: it does not re-test business logic (that's the other e2e
 * specs' job), only that the visual layer didn't break page rendering.
 */

const PUBLIC_ROUTES = ["/login", "/register", "/events"];

// This spec's assertions match English UI copy (e.g. "Log in"); the app now defaults to "tr"
// (Turkish-market demo, lib/i18n/LocaleContext.tsx) — force "en" for every navigation here.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => window.localStorage.setItem("ticketing_locale", "en"));
});

for (const route of PUBLIC_ROUTES) {
  test(`${route} renders on the dark control-panel theme with no console errors`, async ({
    page,
  }) => {
    const consoleErrors: string[] = [];
    page.on("console", (msg) => {
      if (msg.type() === "error") consoleErrors.push(msg.text());
    });
    const pageErrors: string[] = [];
    page.on("pageerror", (err) => pageErrors.push(err.message));

    await page.goto(route);
    await expect(page.locator("body")).toBeVisible();

    // Dark base surface from app/globals.css's --bg token (#0a0c10), not the Next.js default white.
    const bodyBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(bodyBg).toBe("rgb(10, 12, 16)");

    // The "Ticketing" wordmark is present everywhere — as a NavBar link on /events, and as
    // AuthLayout's plain (non-link) wordmark above the card on /login and /register.
    await expect(page.getByText("Ticketing").first()).toBeVisible();

    expect(pageErrors, `unexpected page errors on ${route}: ${pageErrors.join("; ")}`).toEqual([]);
    expect(
      consoleErrors,
      `unexpected console errors on ${route}: ${consoleErrors.join("; ")}`,
    ).toEqual([]);
  });
}

test("login form uses the restyled input/button primitives", async ({ page }) => {
  await page.goto("/login");
  const emailInput = page.locator("#login-email");
  await expect(emailInput).toBeVisible();
  await expect(emailInput).toHaveClass(/input-field/);
  await expect(page.getByRole("button", { name: /log in/i })).toHaveClass(/btn-primary/);
});

test("events catalog cards render as interactive panels with status badges", async ({ page }) => {
  await page.goto("/events");
  const firstCard = page.getByRole("link").filter({ hasText: /Live|Draft|Sold out|Closed/ }).first();
  await expect(firstCard).toBeVisible({ timeout: 10_000 });
  await expect(firstCard).toHaveClass(/panel/);
});
