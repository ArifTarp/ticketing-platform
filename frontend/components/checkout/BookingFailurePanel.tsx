import Link from "next/link";
import type { BookingResponse } from "@/types/booking";

interface BookingFailurePanelProps {
  booking: BookingResponse;
}

/**
 * Failure view for CANCELLED/EXPIRED — screen 6. `BookingResponse` carries no `reason` string
 * (that only exists on the Kafka `BookingCancelledEvent`, not the REST DTO), so the wording is
 * generic per status rather than surfacing a specific saga-level reason.
 */
function reasonFor(status: BookingResponse["status"]): string {
  if (status === "EXPIRED") {
    return "Your hold expired before payment completed.";
  }
  return "Payment could not be completed.";
}

export function BookingFailurePanel({ booking }: BookingFailurePanelProps) {
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
        {booking.status === "EXPIRED" ? "Expired" : "Cancelled"}
      </span>
      <h1 className="font-[family-name:var(--font-display)] text-lg font-semibold text-[var(--text-primary)]">
        Booking not completed
      </h1>
      <p className="text-sm text-[var(--text-secondary)]">{reasonFor(booking.status)}</p>
      <Link href={`/events/${booking.eventId}/seats`} className="btn btn-primary">
        Try again
      </Link>
    </div>
  );
}
