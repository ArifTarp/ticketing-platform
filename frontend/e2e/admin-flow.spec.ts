import { execSync } from "node:child_process";
import { test, expect } from "@playwright/test";

/**
 * Verifies the admin screen's ADMIN-gating and core CRUD flow still work after the redesign.
 * There's no product-level way to grant ADMIN (registration only ever assigns USER, per
 * services/auth's schema/seed), so this test promotes its own throwaway user directly in
 * Postgres — the same database docker-compose's `postgres` container backs in this dev
 * environment — then re-logs-in so the fresh JWT actually carries the ADMIN role claim (roles
 * are baked into the token at issue time, so promoting mid-session wouldn't retroactively
 * upgrade an already-issued token).
 */

const adminEmail = `e2e-admin-${Date.now()}@example.com`;
const password = "TestPass123!";

test.describe.configure({ mode: "serial" });

test.beforeAll(() => {
  execSync(
    `docker exec ticketing-postgres psql -U ticketing -d ticketing_auth -c ` +
      `"INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u, roles r WHERE u.email = '${adminEmail}' AND r.name = 'ADMIN' ON CONFLICT DO NOTHING;"`,
    { stdio: "pipe" },
  );
});

test("a non-admin user is blocked from /admin/events", async ({ page }) => {
  await page.goto("/register");
  await page.locator("#register-email").fill(`e2e-user-${Date.now()}@example.com`);
  await page.locator("#register-password").fill(password);
  await page.locator("#register-confirm-password").fill(password);
  await page.getByRole("button", { name: /create account|continue|register/i }).click();
  await expect(page).toHaveURL(/\/events$/, { timeout: 10_000 });

  await page.goto("/admin/events");
  await expect(page.getByText(/don't have access|forbidden|not allowed/i)).toBeVisible({
    timeout: 10_000,
  });
});

test("an admin user can reach /admin/events, see the table, and create a venue + event", async ({
  page,
}) => {
  // Register first (this creates the user row the beforeAll's SQL then promotes)...
  await page.goto("/register");
  await page.locator("#register-email").fill(adminEmail);
  await page.locator("#register-password").fill(password);
  await page.locator("#register-confirm-password").fill(password);
  await page.getByRole("button", { name: /create account|continue|register/i }).click();
  await expect(page).toHaveURL(/\/events$/, { timeout: 10_000 });

  // ...promote to ADMIN now that the user row exists...
  execSync(
    `docker exec ticketing-postgres psql -U ticketing -d ticketing_auth -c ` +
      `"INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u, roles r WHERE u.email = '${adminEmail}' AND r.name = 'ADMIN' ON CONFLICT DO NOTHING;"`,
    { stdio: "pipe" },
  );

  // ...then log out and back in so the new JWT actually carries the ADMIN role claim.
  // NavBar's logout() only clears the session (localStorage + context) — it doesn't navigate —
  // so go to /login explicitly rather than expecting an automatic redirect.
  await page.getByRole("button", { name: /log out/i }).click();
  await expect(page.getByRole("link", { name: /^log in$/i })).toBeVisible();
  await page.goto("/login");
  await page.locator("#login-email").fill(adminEmail);
  await page.locator("#login-password").fill(password);
  await page.getByRole("button", { name: /continue|log in|sign in/i }).click();
  await expect(page).toHaveURL(/\/events$/, { timeout: 10_000 });

  await page.getByRole("link", { name: /admin/i }).click();
  await expect(page).toHaveURL(/\/admin\/events$/);
  await expect(page.getByRole("heading", { name: /admin/i })).toBeVisible();

  // Table renders with the seeded demo events, styled as .panel.
  await expect(page.getByText(/neon nights live/i)).toBeVisible({ timeout: 10_000 });

  // Create a venue + event through the form.
  await page.getByRole("button", { name: /\+ create event/i }).click();
  const venueName = `E2E Venue ${Date.now()}`;
  await page.getByRole("button", { name: /\+ new venue/i }).click();
  await page.getByLabel(/venue name/i).fill(venueName);
  await page.getByLabel(/^address$/i).fill("1 Test Way");
  await page.getByLabel(/^city$/i).fill("Testville");
  await page.getByRole("button", { name: /^create venue$/i }).click();
  // The venue mini-form collapses back into the <select> once created — the new option now
  // exists in it (and is selected), even though a native select only *renders* the selected
  // option's text in its closed state.
  await expect(page.locator("select").first()).toContainText(venueName, { timeout: 10_000 });

  const eventTitle = `E2E Event ${Date.now()}`;
  await page.getByLabel(/^title$/i).fill(eventTitle);
  await page.getByLabel(/starts at/i).fill("2027-01-01T20:00");
  await page.getByPlaceholder(/name \(e\.g\. vip\)/i).fill("General");
  await page.getByPlaceholder(/^section$/i).fill("A");
  await page.getByPlaceholder(/^price$/i).fill("50");
  await page.getByRole("button", { name: /^save event$/i }).click();

  await expect(page.getByText(eventTitle)).toBeVisible({ timeout: 10_000 });
});
