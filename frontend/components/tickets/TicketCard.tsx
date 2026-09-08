import type { BookingResponse } from "@/types/booking";
import type { EventResponse } from "@/types/event";
import { formatPrice } from "@/lib/formatPrice";
import { formatEventDate } from "@/lib/formatDate";
import { QrCodeDisplay } from "./QrCodeDisplay";

interface TicketCardProps {
  booking: BookingResponse;
  /** Event detail merged client-side by eventId (screen 7 spec); undefined while loading, null on fetch failure. */
  event: EventResponse | null | undefined;
}

/** One CONFIRMED booking: event info, seat list, total, and a QrCodeDisplay of the booking id. */
export function TicketCard({ booking, event }: TicketCardProps) {
  return (
    <div className="flex flex-col gap-4 rounded-lg border border-zinc-200 bg-white p-4 shadow-sm sm:flex-row sm:items-center sm:justify-between">
      <div className="flex flex-col gap-2">
        {event === undefined && (
          <div className="h-4 w-40 animate-pulse rounded bg-zinc-200" />
        )}
        {event === null && (
          <h3 className="text-base font-semibold text-zinc-900">Event #{booking.eventId}</h3>
        )}
        {event && (
          <>
            <h3 className="text-base font-semibold text-zinc-900">{event.title}</h3>
            <p className="text-sm text-zinc-500">
              {event.venue.name} · {formatEventDate(event.startsAt)}
            </p>
          </>
        )}
        <ul className="flex flex-wrap gap-x-3 gap-y-1 text-sm text-zinc-700">
          {booking.items.map((item) => (
            <li key={item.seatId}>Seat #{item.seatId}</li>
          ))}
        </ul>
        <p className="text-sm font-medium text-zinc-900">Total: {formatPrice(booking.total)}</p>
      </div>
      <div className="flex flex-col items-center gap-2">
        <QrCodeDisplay value={String(booking.id)} />
        <span className="text-xs text-zinc-500">Booking #{booking.id}</span>
      </div>
    </div>
  );
}
