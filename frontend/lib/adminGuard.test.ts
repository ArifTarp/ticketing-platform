import { describe, expect, it } from "vitest";
import { resolveAdminGuardStatus } from "./adminGuard";

describe("resolveAdminGuardStatus", () => {
  it("returns loading while the session is still resolving, regardless of auth state", () => {
    expect(
      resolveAdminGuardStatus({ isLoading: true, isAuthenticated: false, roles: undefined }),
    ).toBe("loading");
    expect(
      resolveAdminGuardStatus({ isLoading: true, isAuthenticated: true, roles: ["ADMIN"] }),
    ).toBe("loading");
  });

  it("returns unauthenticated for a logged-out user", () => {
    expect(
      resolveAdminGuardStatus({ isLoading: false, isAuthenticated: false, roles: undefined }),
    ).toBe("unauthenticated");
  });

  it("returns forbidden for an authenticated non-ADMIN user", () => {
    expect(
      resolveAdminGuardStatus({ isLoading: false, isAuthenticated: true, roles: ["USER"] }),
    ).toBe("forbidden");
  });

  it("returns forbidden when roles is empty or missing despite being authenticated", () => {
    expect(
      resolveAdminGuardStatus({ isLoading: false, isAuthenticated: true, roles: [] }),
    ).toBe("forbidden");
    expect(
      resolveAdminGuardStatus({ isLoading: false, isAuthenticated: true, roles: undefined }),
    ).toBe("forbidden");
  });

  it("returns allowed for an authenticated ADMIN user", () => {
    expect(
      resolveAdminGuardStatus({ isLoading: false, isAuthenticated: true, roles: ["USER", "ADMIN"] }),
    ).toBe("allowed");
  });
});
