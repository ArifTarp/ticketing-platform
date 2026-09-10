import Link from "next/link";
import type { BookingResponse } from "@/types/booking";
import { useLocale } from "@/lib/i18n/LocaleContext";

interface BookingFailurePanelProps {
  booking: BookingResponse;
}

/**
 * Failure view for CANCELLED/EXPIRED — screen 6. `BookingResponse` carries no `reason` string
 * (that only exists on the Kafka `BookingCancelledEvent`, not the REST DTO), so the wording is
 * generic per status rather than surfacing a specific saga-level reason.
 */
function reasonKeyFor(status: BookingResponse["status"]): string {
  if (status === "EXPIRED") {
    return "confirm.reasonExpired";
  }
  return "confirm.reasonFailed";
}

export function BookingFailurePanel({ booking }: BookingFailurePanelProps) {
  const { t } = useLocale();

  return (
    <div
      className="panel flex flex-col items-center gap-4 px-6 py-10 text-center"
      style={{ borderColor: "var(--status-danger)", boxShadow: "0 0 0 1px var(--status-danger-soft)" }}
    >
      <div
        className="flex h-10 w-10 items-center justify-center rounded-full text-base"
        style={{ backgroundColor: "var(--status-danger-soft)", color: "var(--status-danger)" }}
      >
        ✕
      </div>
      <span
        className="label-mono rounded-full px-2.5 py-1"
        style={{ color: "var(--status-danger)", backgroundColor: "var(--status-danger-soft)" }}
      >
        {booking.status === "EXPIRED" ? t("confirm.expiredBadge") : t("confirm.cancelledBadge")}
      </span>
      <h1 className="font-[family-name:var(--font-display)] text-lg font-semibold text-[var(--text-primary)]">
        {t("confirm.notCompleted")}
      </h1>
      <p className="text-sm text-[var(--text-secondary)]">{t(reasonKeyFor(booking.status))}</p>
      <Link href={`/events/${booking.eventId}/seats`} className="btn btn-primary">
        {t("confirm.tryAgain")}
      </Link>
    </div>
  );
}
