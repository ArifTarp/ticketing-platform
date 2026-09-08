import type { JwtClaims } from "@/types/auth";

/**
 * Decodes a JWT's payload segment without verifying its signature — signature verification
 * happens server-side at the gateway. Used purely to read display claims (email, roles, exp)
 * client-side. Returns null for a malformed token instead of throwing.
 */
export function decodeJwt(token: string): JwtClaims | null {
  const parts = token.split(".");
  if (parts.length !== 3) {
    return null;
  }

  try {
    const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "=");
    const json =
      typeof window === "undefined"
        ? Buffer.from(padded, "base64").toString("utf-8")
        : window.atob(padded);
    return JSON.parse(json) as JwtClaims;
  } catch {
    return null;
  }
}

/** True when the token's `exp` claim is in the past (or the token can't be decoded). */
export function isJwtExpired(token: string): boolean {
  const claims = decodeJwt(token);
  if (!claims) {
    return true;
  }
  return claims.exp * 1000 <= Date.now();
}
