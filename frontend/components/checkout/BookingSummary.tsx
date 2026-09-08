import type { BookingResponse } from "@/types/booking";
import { formatPrice } from "@/lib/formatPrice";
import { CountdownTimer } from "@/components/common/CountdownTimer";

interface BookingSummaryProps {
  booking: BookingResponse;
}

/** Seats + total for the current booking, reused by screens 5 and 6. */
export function BookingSummary({ booking }: BookingSummaryProps) {
  return (
    <div className="flex flex-col gap-4 rounded-lg border border-zinc-200 bg-white p-4">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-zinc-900">Booking summary</h2>
        {booking.status === "PENDING" && <CountdownTimer expiresAt={booking.expiresAt} />}
      </div>
      <ul className="flex flex-col gap-1.5 text-sm">
        {booking.items.map((item) => (
          <li key={item.seatId} className="flex items-center justify-between">
            <span className="text-zinc-700">Seat #{item.seatId}</span>
            <span className="text-zinc-900">{formatPrice(item.price)}</span>
          </li>
        ))}
      </ul>
      <div className="flex items-center justify-between border-t border-zinc-200 pt-3 text-sm font-semibold">
        <span>Total</span>
        <span>{formatPrice(booking.total)}</span>
      </div>
    </div>
  );
}
