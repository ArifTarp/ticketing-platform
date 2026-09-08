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
    <div className="flex flex-col items-center gap-4 rounded-lg border border-red-200 bg-red-50 px-6 py-10 text-center">
      <div className="flex h-10 w-10 items-center justify-center rounded-full bg-red-600 text-white">
        ✕
      </div>
      <h1 className="text-lg font-semibold text-zinc-900">Booking not completed</h1>
      <p className="text-sm text-zinc-700">{reasonFor(booking.status)}</p>
      <Link
        href={`/events/${booking.eventId}/seats`}
        className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-700"
      >
        Try again
      </Link>
    </div>
  );
}
