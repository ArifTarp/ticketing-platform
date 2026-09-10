import { useCountdown } from "@/hooks/useCountdown";
import { useLocale } from "@/lib/i18n/LocaleContext";

interface CountdownTimerProps {
  /** ISO instant the hold expires at, or null when no hold is active yet. */
  expiresAt: string | null;
}

/** Shared mm:ss hold-TTL countdown for screens 4 (seat selection) and 5 (checkout). */
export function CountdownTimer({ expiresAt }: CountdownTimerProps) {
  const { t } = useLocale();
  const { label, isExpiring, isExpired } = useCountdown(expiresAt);

  if (!expiresAt) {
    return null;
  }

  const tone = isExpired ? "danger" : isExpiring ? "pending" : "neutral";
  const toneVars = {
    danger: { color: "var(--status-danger)", bg: "var(--status-danger-soft)" },
    pending: { color: "var(--status-pending)", bg: "var(--status-pending-soft)" },
    neutral: { color: "var(--text-secondary)", bg: "var(--surface)" },
  }[tone];

  return (
    <div
      className="value-mono flex items-center gap-2 rounded-[var(--radius-sm)] border px-3 py-1.5 text-sm font-medium"
      style={{ borderColor: toneVars.color, backgroundColor: toneVars.bg, color: toneVars.color }}
    >
      <span
        className={`status-dot ${!isExpired ? "status-dot-pulse" : ""}`}
        style={{ backgroundColor: toneVars.color }}
        aria-hidden
      />
      {isExpired ? t("seats.countdown.expired") : t("seats.countdown.expiresIn", { label })}
    </div>
  );
}
