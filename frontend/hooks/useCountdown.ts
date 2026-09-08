"use client";

import { useEffect, useState } from "react";

/** Below this remaining time, CountdownTimer switches to a warning visual treatment. */
export const EXPIRING_SOON_THRESHOLD_MS = 2 * 60 * 1000;

/** Milliseconds remaining until `expiresAt`, floored at 0. Exported for direct unit testing. */
export function msUntil(expiresAt: string, now: number = Date.now()): number {
  return Math.max(0, new Date(expiresAt).getTime() - now);
}

/** Formats remaining milliseconds as `mm:ss`. Exported for direct unit testing. */
export function formatCountdown(remainingMs: number): string {
  const totalSeconds = Math.floor(remainingMs / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

export interface UseCountdownResult {
  remainingMs: number;
  label: string;
  isExpiring: boolean;
  isExpired: boolean;
}

/**
 * Derives a ticking mm:ss countdown from a hold's `expiresAt` ISO instant — shared by screen 4
 * (seat selection) and screen 5 (checkout), both of which show the same live Redis-hold TTL.
 */
export function useCountdown(expiresAt: string | null): UseCountdownResult {
  const [remainingMs, setRemainingMs] = useState(() => (expiresAt ? msUntil(expiresAt) : 0));

  useEffect(() => {
    if (!expiresAt) {
      setRemainingMs(0);
      return;
    }
    setRemainingMs(msUntil(expiresAt));
    const interval = setInterval(() => {
      setRemainingMs(msUntil(expiresAt));
    }, 1000);
    return () => clearInterval(interval);
  }, [expiresAt]);

  return {
    remainingMs,
    label: formatCountdown(remainingMs),
    isExpiring: remainingMs > 0 && remainingMs <= EXPIRING_SOON_THRESHOLD_MS,
    isExpired: expiresAt !== null && remainingMs <= 0,
  };
}
