import { useCountdown } from "@/hooks/useCountdown";

interface CountdownTimerProps {
  /** ISO instant the hold expires at, or null when no hold is active yet. */
  expiresAt: string | null;
}

/** Shared mm:ss hold-TTL countdown for screens 4 (seat selection) and 5 (checkout). */
export function CountdownTimer({ expiresAt }: CountdownTimerProps) {
  const { label, isExpiring, isExpired } = useCountdown(expiresAt);

  if (!expiresAt) {
    return null;
  }

  return (
    <div
      className={`rounded-md border px-3 py-1.5 text-sm font-medium ${
        isExpired
          ? "border-red-300 bg-red-50 text-red-700"
          : isExpiring
            ? "border-amber-300 bg-amber-50 text-amber-700"
            : "border-zinc-200 bg-white text-zinc-700"
      }`}
    >
      {isExpired ? "Hold expired" : `Hold expires in ${label}`}
    </div>
  );
}
