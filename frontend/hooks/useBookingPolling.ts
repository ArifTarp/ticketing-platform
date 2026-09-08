"use client";

import { useEffect, useState } from "react";
import { getBooking } from "@/lib/bookingApi";
import { ApiRequestError } from "@/lib/apiClient";
import type { BookingResponse, BookingStatus } from "@/types/booking";

/** Statuses that end the checkout saga — polling stops once one of these is observed. */
export const TERMINAL_BOOKING_STATUSES: BookingStatus[] = ["CONFIRMED", "CANCELLED", "EXPIRED"];

export const DEFAULT_POLL_INTERVAL_MS = 1500;
export const DEFAULT_POLL_TIMEOUT_MS = 60_000;

export interface UseBookingPollingOptions {
  intervalMs?: number;
  timeoutMs?: number;
}

export interface UseBookingPollingResult {
  booking: BookingResponse | null;
  isPolling: boolean;
  hasTimedOut: boolean;
  error: string | null;
}

/**
 * Polls GET /api/v1/bookings/{bookingId} until `status` leaves PENDING (screen 5's wait for the
 * async checkout saga's result) or a client-side poll-timeout elapses.
 */
export function useBookingPolling(
  bookingId: string | number | null,
  options: UseBookingPollingOptions = {},
): UseBookingPollingResult {
  const { intervalMs = DEFAULT_POLL_INTERVAL_MS, timeoutMs = DEFAULT_POLL_TIMEOUT_MS } = options;
  const [booking, setBooking] = useState<BookingResponse | null>(null);
  const [isPolling, setIsPolling] = useState(true);
  const [hasTimedOut, setHasTimedOut] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (bookingId === null) {
      return;
    }

    let isCancelled = false;
    const intervalHandle: { current: ReturnType<typeof setInterval> | undefined } = { current: undefined };

    setIsPolling(true);
    setHasTimedOut(false);
    setError(null);

    const stop = () => {
      setIsPolling(false);
      clearInterval(intervalHandle.current);
    };

    const tick = async () => {
      try {
        const result = await getBooking(bookingId);
        if (isCancelled) {
          return;
        }
        setBooking(result);
        if (TERMINAL_BOOKING_STATUSES.includes(result.status)) {
          stop();
        }
      } catch (err) {
        if (isCancelled) {
          return;
        }
        setError(err instanceof ApiRequestError ? err.detail : "Failed to check booking status.");
        stop();
      }
    };

    void tick();
    intervalHandle.current = setInterval(tick, intervalMs);
    const timeoutHandle = setTimeout(() => {
      if (isCancelled) {
        return;
      }
      setHasTimedOut(true);
      stop();
    }, timeoutMs);

    return () => {
      isCancelled = true;
      clearInterval(intervalHandle.current);
      clearTimeout(timeoutHandle);
    };
  }, [bookingId, intervalMs, timeoutMs]);

  return { booking, isPolling, hasTimedOut, error };
}
