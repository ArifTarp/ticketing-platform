/** Base URL of the gateway (the only backend door the frontend talks to). */
export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

/** localStorage key the JWT is persisted under (see SessionProvider). */
export const TOKEN_STORAGE_KEY = "ticketing_jwt";

/** Business rule: at most 6 seats per booking (used from Phase 12 onward). */
export const MAX_SEATS_PER_BOOKING = 6;

/**
 * sessionStorage key prefix marking that `POST /bookings/{id}/checkout` (the single, sole saga
 * trigger — business-rules.md) has already fired for a given bookingId. Set by the seats screen's
 * "Proceed to payment" action right before navigating to `/checkout/[bookingId]`; read by the
 * checkout screen so its "Pay now" fallback only calls checkout() when it truly hasn't fired yet
 * (e.g. a bookmarked/shared/duplicate-tab URL that lands directly on the checkout screen).
 */
export const CHECKOUT_TRIGGERED_STORAGE_KEY_PREFIX = "ticketing_checkout_triggered_";
