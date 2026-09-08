/** Base URL of the gateway (the only backend door the frontend talks to). */
export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

/** localStorage key the JWT is persisted under (see SessionProvider). */
export const TOKEN_STORAGE_KEY = "ticketing_jwt";

/** Business rule: at most 6 seats per booking (used from Phase 12 onward). */
export const MAX_SEATS_PER_BOOKING = 6;
