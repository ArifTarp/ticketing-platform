import { act } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderHook } from "@/test/renderHook";
import { useSeatSelection } from "./useSeatSelection";
import { ApiRequestError } from "@/lib/apiClient";
import type { SeatDto } from "@/types/event";

vi.mock("@/lib/bookingApi", () => ({
  holdSeats: vi.fn(),
}));

import { holdSeats } from "@/lib/bookingApi";

function makeSeat(seatId: number, price = 100): SeatDto {
  return { seatId, section: "A", row: "1", number: seatId, seatCategory: { id: 1, name: "Standard", price } };
}

describe("useSeatSelection", () => {
  afterEach(() => {
    vi.mocked(holdSeats).mockReset();
  });

  it("toggles a seat into and out of the selection, tracking the running total", () => {
    const { result } = renderHook(() => useSeatSelection({ eventId: 1, userId: 1 }));
    const seat = makeSeat(10, 120);

    act(() => {
      result.current!.toggleSeat(seat);
    });
    expect(result.current!.selectedSeatIds.has(10)).toBe(true);
    expect(result.current!.total).toBe(120);

    act(() => {
      result.current!.toggleSeat(seat);
    });
    expect(result.current!.selectedSeatIds.has(10)).toBe(false);
    expect(result.current!.total).toBe(0);
  });

  it("refuses to select a 7th seat", () => {
    const { result } = renderHook(() => useSeatSelection({ eventId: 1, userId: 1 }));

    act(() => {
      for (let i = 1; i <= 7; i += 1) {
        result.current!.toggleSeat(makeSeat(i));
      }
    });

    expect(result.current!.selectedSeats).toHaveLength(6);
    expect(result.current!.maxSeatsReached).toBe(true);
  });

  it("holdSelectedSeats calls holdSeats with the selected seats and stores the resulting booking", async () => {
    const booking = {
      id: 5,
      userId: 1,
      eventId: 1,
      status: "PENDING" as const,
      total: 120,
      createdAt: "2026-01-01T00:00:00Z",
      expiresAt: "2026-01-01T00:10:00Z",
      items: [{ seatId: 10, price: 120 }],
    };
    vi.mocked(holdSeats).mockResolvedValue(booking);

    const { result } = renderHook(() => useSeatSelection({ eventId: 1, userId: 1 }));
    act(() => {
      result.current!.toggleSeat(makeSeat(10, 120));
    });

    await act(async () => {
      await result.current!.holdSelectedSeats();
    });

    expect(holdSeats).toHaveBeenCalledWith({
      userId: 1,
      eventId: 1,
      seats: [{ seatId: 10, price: 120 }],
    });
    expect(result.current!.booking).toEqual(booking);
  });

  it("stores a friendly message when holdSeats rejects with a 409", async () => {
    vi.mocked(holdSeats).mockRejectedValue(
      new ApiRequestError({ status: 409, title: "Conflict", detail: "Seat 10 is already held" }),
    );

    const { result } = renderHook(() => useSeatSelection({ eventId: 1, userId: 1 }));
    act(() => {
      result.current!.toggleSeat(makeSeat(10));
    });

    await act(async () => {
      await expect(result.current!.holdSelectedSeats()).rejects.toThrow();
    });

    expect(result.current!.holdError).toBe("Seat 10 is already held");
  });
});
