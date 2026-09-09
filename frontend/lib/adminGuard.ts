const ADMIN_ROLE = "ADMIN";

export type AdminGuardStatus = "loading" | "unauthenticated" | "forbidden" | "allowed";

export interface AdminGuardInput {
  /** Mirrors SessionProvider's isLoading — true until the initial localStorage read completes. */
  isLoading: boolean;
  isAuthenticated: boolean;
  /** SessionProvider's user.roles, or undefined when logged out. */
  roles: string[] | undefined;
}

/**
 * Pure role-gating decision for `AdminGuard` (docs/user-flow.md screen 8), kept side-effect-free
 * so the "ADMIN passes / non-ADMIN or logged-out is blocked, no API call attempted" rule is
 * unit-testable without rendering React (this repo has no @testing-library/react dependency).
 * `AdminGuard.tsx` only renders its children (EventTable/EventForm) when this resolves to
 * "allowed".
 */
export function resolveAdminGuardStatus({
  isLoading,
  isAuthenticated,
  roles,
}: AdminGuardInput): AdminGuardStatus {
  if (isLoading) {
    return "loading";
  }
  if (!isAuthenticated) {
    return "unauthenticated";
  }
  if (!roles || !roles.includes(ADMIN_ROLE)) {
    return "forbidden";
  }
  return "allowed";
}
