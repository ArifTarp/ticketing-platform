import Link from "next/link";
import type { BookingResponse } from "@/types/booking";
import { formatPrice } from "@/lib/formatPrice";

interface BookingSuccessPanelProps {
  booking: BookingResponse;
}

/** Success view for a CONFIRMED booking — screen 6. */
export function BookingSuccessPanel({ booking }: BookingSuccessPanelProps) {
  return (
    <div className="flex flex-col items-center gap-4 rounded-lg border border-emerald-200 bg-emerald-50 px-6 py-10 text-center">
      <div className="flex h-10 w-10 items-center justify-center rounded-full bg-emerald-600 text-white">
        ✓
      </div>
      <h1 className="text-lg font-semibold text-zinc-900">Booking confirmed</h1>
      <ul className="flex flex-col gap-1 text-sm text-zinc-700">
        {booking.items.map((item) => (
          <li key={item.seatId}>Seat #{item.seatId}</li>
        ))}
      </ul>
      <p className="text-sm font-medium text-zinc-900">Total: {formatPrice(booking.total)}</p>
      <Link
        href="/tickets"
        className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-700"
      >
        View my tickets
      </Link>
    </div>
  );
}
