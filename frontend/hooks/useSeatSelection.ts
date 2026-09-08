"use client";

import { useCallback, useMemo, useState } from "react";
import { holdSeats } from "@/lib/bookingApi";
import { ApiRequestError } from "@/lib/apiClient";
import { MAX_SEATS_PER_BOOKING } from "@/lib/constants";
import type { SeatDto } from "@/types/event";
import type { BookingResponse } from "@/types/booking";

export interface UseSeatSelectionOptions {
  eventId: string | number;
  userId: string | number;
}

export interface UseSeatSelectionResult {
  selectedSeats: SeatDto[];
  selectedSeatIds: Set<number>;
  total: number;
  booking: BookingResponse | null;
  isHolding: boolean;
  holdError: string | null;
  maxSeatsReached: boolean;
  toggleSeat: (seat: SeatDto) => void;
  clearSelection: () => void;
  holdSelectedSeats: () => Promise<BookingResponse>;
}

/**
 * Screen 4's seat-selection state: which seats the user has picked (client-only until the hold
 * call succeeds), the running total, and the resulting PENDING booking once held.
 *
 * Holds are requested in a single batched `POST /bookings/hold` call for every currently selected
 * seat, not one call per click — `BookingHoldService.holdSeats` (services/booking) always creates
 * a brand-new `Booking` per call and re-validates every seat's `SeatAvailability` is `AVAILABLE`,
 * so re-sending a seat this same session already holds would conflict with itself. Deviates from
 * `docs/user-flow.md`'s literal "on each click" wording for this reason — flagged for
 * `backend-architecture`/`workflow-rules`; a real "add seat to existing pending booking" endpoint
 * would remove the need for this batching.
 */
export function useSeatSelection({ eventId, userId }: UseSeatSelectionOptions): UseSeatSelectionResult {
  const [selectedSeats, setSelectedSeats] = useState<SeatDto[]>([]);
  const [booking, setBooking] = useState<BookingResponse | null>(null);
  const [isHolding, setIsHolding] = useState(false);
  const [holdError, setHoldError] = useState<string | null>(null);

  const selectedSeatIds = useMemo(
    () => new Set(selectedSeats.map((seat) => seat.seatId)),
    [selectedSeats],
  );

  const toggleSeat = useCallback((seat: SeatDto) => {
    setHoldError(null);
    setSelectedSeats((current) => {
      const isSelected = current.some((s) => s.seatId === seat.seatId);
      if (isSelected) {
        return current.filter((s) => s.seatId !== seat.seatId);
      }
      if (current.length >= MAX_SEATS_PER_BOOKING) {
        return current;
      }
      return [...current, seat];
    });
  }, []);

  const clearSelection = useCallback(() => {
    setSelectedSeats([]);
    setBooking(null);
    setHoldError(null);
  }, []);

  const holdSelectedSeats = useCallback(async (): Promise<BookingResponse> => {
    if (selectedSeats.length === 0) {
      throw new Error("No seats selected");
    }
    setIsHolding(true);
    setHoldError(null);
    try {
      const result = await holdSeats({
        userId: Number(userId),
        eventId: Number(eventId),
        seats: selectedSeats.map((seat) => ({ seatId: seat.seatId, price: seat.seatCategory.price })),
      });
      setBooking(result);
      return result;
    } catch (err) {
      setHoldError(
        err instanceof ApiRequestError ? err.detail : "Failed to hold the selected seats.",
      );
      throw err;
    } finally {
      setIsHolding(false);
    }
  }, [selectedSeats, eventId, userId]);

  const total = useMemo(
    () => selectedSeats.reduce((sum, seat) => sum + seat.seatCategory.price, 0),
    [selectedSeats],
  );

  return {
    selectedSeats,
    selectedSeatIds,
    total,
    booking,
    isHolding,
    holdError,
    maxSeatsReached: selectedSeats.length >= MAX_SEATS_PER_BOOKING,
    toggleSeat,
    clearSelection,
    holdSelectedSeats,
  };
}
