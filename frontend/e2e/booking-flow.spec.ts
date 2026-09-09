import { test, expect } from "@playwright/test";

/**
 * The golden path end-to-end, run against the real gateway + backend services (not mocked) —
 * this is what actually proves the UI redesign didn't break functionality: register -> browse ->
 * pick an event -> hold seats -> pay (mock) -> confirmed booking -> ticket appears in /tickets.
 *
 * Uses event #2 ("Acoustic Evening", $80/seat General admission) deliberately: 2 seats = $160,
 * comfortably under the payment mock's fail-at->=$500 threshold (services/payment's
 * PaymentMockRule), so the payment outcome is deterministic here.
 */

test.describe.configure({ mode: "serial" });

const uniqueEmail = `e2e-${Date.now()}@example.com`;
const password = "TestPass123!";

test("register, hold seats, pay, and see the confirmed ticket", async ({ page }) => {
  // 1. Register — auto-logs in and redirects to /events.
  await page.goto("/register");
  await page.locator("#register-email").fill(uniqueEmail);
  await page.locator("#register-password").fill(password);
  await page.locator("#register-confirm-password").fill(password);
  await page.getByRole("button", { name: /create account|continue|register/i }).click();
  await expect(page).toHaveURL(/\/events$/, { timeout: 10_000 });
  await expect(page.getByText(uniqueEmail)).toBeVisible();

  // 2. Open the "Acoustic Evening" event detail.
  await page.getByRole("link", { name: /acoustic evening/i }).click();
  await expect(page).toHaveURL(/\/events\/\d+$/);
  await expect(page.getByRole("heading", { name: /acoustic evening/i })).toBeVisible();

  // 3. Go to seat selection (a <button> that router.push()es, not a <Link>).
  await page.getByRole("button", { name: /select seats/i }).click();
  await expect(page).toHaveURL(/\/seats$/);

  // 4. Select 2 available "General" seats and hold them.
  const availableSeats = page.locator('button[title*="General"]:not([disabled])');
  await expect(availableSeats.first()).toBeVisible({ timeout: 10_000 });
  await availableSeats.nth(0).click();
  await availableSeats.nth(1).click();
  await expect(page.getByText("$160.00")).toBeVisible();
  await page.getByRole("button", { name: /hold selected seats/i }).click();
  await expect(page.getByRole("button", { name: /proceed to payment/i })).toBeVisible({
    timeout: 10_000,
  });

  // 5. Proceed to checkout. `handleProceedToPayment` (seats page) already calls
  // POST /bookings/{id}/checkout before navigating here — on this local stack the saga
  // (booking -> payment.commands -> payment -> payment.events -> booking) usually resolves
  // before the checkout page even finishes its first render, so it typically redirects straight
  // to /confirm without the mock "Pay now" card form ever appearing (that form is only the
  // fallback path for a bookmarked/reloaded checkout URL — see the checkout page's own comment).
  // Handle both timings: fill the card form only if it's actually shown.
  await page.getByRole("button", { name: /proceed to payment/i }).click();
  await page.waitForURL(/\/checkout\/\d+(\/confirm)?$/, { timeout: 15_000 });
  if (/\/checkout\/\d+$/.test(page.url())) {
    const cardInput = page.getByPlaceholder("4242 4242 4242 4242");
    if (await cardInput.isVisible().catch(() => false)) {
      await cardInput.fill("4242424242424242");
      await page.getByPlaceholder("MM/YY").fill("12/30");
      await page.getByPlaceholder("123").fill("123");
      await page.getByRole("button", { name: /pay now/i }).click();
    }
  }

  // 6. Either way, the checkout page polls until the saga resolves, then redirects to /confirm.
  await expect(page).toHaveURL(/\/checkout\/\d+\/confirm$/, { timeout: 20_000 });
  await expect(page.getByRole("heading", { name: /booking confirmed/i })).toBeVisible();

  // 7. The ticket now shows up on /tickets.
  await page.goto("/tickets");
  await expect(page.getByText(/acoustic evening/i).first()).toBeVisible({ timeout: 10_000 });
  await expect(page.getByText(/confirmed/i).first()).toBeVisible();
});
