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
  // `remainingMs` is derived straight from `expiresAt` on every render — never held in state —
  // so a change in `expiresAt` (e.g. a hold just succeeded, expiresAt going from null to ten
  // minutes out) is reflected immediately. `tick` exists purely to force a re-render once a
  // second so the derived value keeps counting down; it carries no time data itself. A previous
  // version stored `remainingMs` in state seeded once on mount and only corrected by an effect on
  // `expiresAt` changes — between the render where `expiresAt` became non-null and that effect
  // running, `remainingMs` was still its stale initial 0, so `isExpired` (which only checks
  // `expiresAt !== null && remainingMs <= 0`) was briefly (and wrongly) `true` right after every
  // hold, which the seat-selection screen's "hold expired" effect took at face value.
  const [, setTick] = useState(0);

  useEffect(() => {
    if (!expiresAt) {
      return;
    }
    const interval = setInterval(() => {
      setTick((value) => value + 1);
    }, 1000);
    return () => clearInterval(interval);
  }, [expiresAt]);

  const remainingMs = expiresAt ? msUntil(expiresAt) : 0;

  return {
    remainingMs,
    label: formatCountdown(remainingMs),
    isExpiring: remainingMs > 0 && remainingMs <= EXPIRING_SOON_THRESHOLD_MS,
    isExpired: expiresAt !== null && remainingMs <= 0,
  };
}
