import { act } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook } from "@/test/renderHook";
import { EXPIRING_SOON_THRESHOLD_MS, formatCountdown, msUntil, useCountdown } from "./useCountdown";

describe("msUntil", () => {
  it("returns the difference between expiresAt and now", () => {
    const now = Date.parse("2026-01-01T00:00:00Z");
    const expiresAt = "2026-01-01T00:05:00Z";
    expect(msUntil(expiresAt, now)).toBe(5 * 60 * 1000);
  });

  it("floors at 0 once expiresAt is in the past", () => {
    const now = Date.parse("2026-01-01T00:10:00Z");
    const expiresAt = "2026-01-01T00:00:00Z";
    expect(msUntil(expiresAt, now)).toBe(0);
  });
});

describe("formatCountdown", () => {
  it("formats whole minutes as mm:00", () => {
    expect(formatCountdown(2 * 60 * 1000)).toBe("2:00");
  });

  it("pads seconds under 10", () => {
    expect(formatCountdown(65 * 1000)).toBe("1:05");
  });

  it("formats 0ms as 0:00", () => {
    expect(formatCountdown(0)).toBe("0:00");
  });
});

describe("useCountdown", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("ticks down every second and flags isExpiring within the warning threshold", () => {
    const now = Date.parse("2026-01-01T00:00:00Z");
    vi.setSystemTime(now);
    const expiresAt = new Date(now + EXPIRING_SOON_THRESHOLD_MS).toISOString();

    const { result } = renderHook(() => useCountdown(expiresAt));
    expect(result.current!.isExpiring).toBe(true);
    expect(result.current!.isExpired).toBe(false);

    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(result.current!.remainingMs).toBeLessThanOrEqual(EXPIRING_SOON_THRESHOLD_MS - 1000);
  });

  it("flags isExpired once expiresAt has passed", () => {
    const now = Date.parse("2026-01-01T00:00:00Z");
    vi.setSystemTime(now);
    const expiresAt = new Date(now - 1000).toISOString();

    const { result } = renderHook(() => useCountdown(expiresAt));
    expect(result.current!.isExpired).toBe(true);
  });

  it("returns 0 remainingMs and no expiry flag when expiresAt is null", () => {
    const { result } = renderHook(() => useCountdown(null));
    expect(result.current!.remainingMs).toBe(0);
    expect(result.current!.isExpired).toBe(false);
  });
});
