import { act } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook } from "@/test/renderHook";
import { useBookingPolling } from "./useBookingPolling";

vi.mock("@/lib/bookingApi", () => ({
  getBooking: vi.fn(),
}));

import { getBooking } from "@/lib/bookingApi";

function pendingBooking() {
  return {
    id: 1,
    userId: 1,
    eventId: 1,
    status: "PENDING" as const,
    total: 100,
    createdAt: "2026-01-01T00:00:00Z",
    expiresAt: "2026-01-01T00:10:00Z",
    items: [],
  };
}

describe("useBookingPolling", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.mocked(getBooking).mockReset();
  });

  it("polls until the booking status leaves PENDING", async () => {
    vi.mocked(getBooking)
      .mockResolvedValueOnce(pendingBooking())
      .mockResolvedValueOnce({ ...pendingBooking(), status: "CONFIRMED" });

    const { result } = renderHook(() => useBookingPolling(1, { intervalMs: 1000, timeoutMs: 10000 }));

    await act(async () => {
      await Promise.resolve();
    });
    expect(result.current!.booking?.status).toBe("PENDING");
    expect(result.current!.isPolling).toBe(true);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });

    expect(result.current!.booking?.status).toBe("CONFIRMED");
    expect(result.current!.isPolling).toBe(false);
    expect(getBooking).toHaveBeenCalledTimes(2);
  });

  it("times out if the booking never resolves within timeoutMs", async () => {
    vi.mocked(getBooking).mockResolvedValue(pendingBooking());

    const { result } = renderHook(() => useBookingPolling(1, { intervalMs: 1000, timeoutMs: 2000 }));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });

    expect(result.current!.hasTimedOut).toBe(true);
    expect(result.current!.isPolling).toBe(false);
  });

  it("does nothing when bookingId is null", async () => {
    const { result } = renderHook(() => useBookingPolling(null));

    await act(async () => {
      await Promise.resolve();
    });

    expect(getBooking).not.toHaveBeenCalled();
    expect(result.current!.booking).toBeNull();
  });
});
