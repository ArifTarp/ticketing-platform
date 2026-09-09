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
    <div className="panel flex flex-col gap-4 p-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex flex-col gap-2">
        <div className="flex items-center gap-2">
          <span className="label-mono">Booking</span>
          <span className="value-mono text-[var(--text-primary)]">#{booking.id}</span>
          <span
            className="label-mono ml-1 inline-flex items-center gap-1.5 rounded-full px-2.5 py-1"
            style={{ color: "var(--status-live)", backgroundColor: "var(--status-live-soft)" }}
          >
            <span className="status-dot" style={{ backgroundColor: "var(--status-live)" }} aria-hidden />
            Confirmed
          </span>
        </div>
        {event === undefined && (
          <div className="h-4 w-40 animate-pulse rounded-[var(--radius-sm)] bg-[var(--surface-hover)]" />
        )}
        {event === null && (
          <h3 className="font-[family-name:var(--font-display)] text-base font-semibold text-[var(--text-primary)]">
            Event #{booking.eventId}
          </h3>
        )}
        {event && (
          <>
            <h3 className="font-[family-name:var(--font-display)] text-base font-semibold text-[var(--text-primary)]">
              {event.title}
            </h3>
            <p className="text-sm text-[var(--text-secondary)]">
              {event.venue.name} · <span className="value-mono">{formatEventDate(event.startsAt)}</span>
            </p>
          </>
        )}
        <ul className="flex flex-wrap gap-x-3 gap-y-1 text-sm">
          {booking.items.map((item) => (
            <li key={item.seatId} className="value-mono text-[var(--text-secondary)]">
              Seat #{item.seatId}
            </li>
          ))}
        </ul>
        <p className="value-mono text-sm font-medium text-[var(--text-primary)]">
          Total: {formatPrice(booking.total)}
        </p>
      </div>
      <div className="flex flex-col items-center gap-2">
        <QrCodeDisplay value={String(booking.id)} />
        <span className="label-mono">Booking #{booking.id}</span>
      </div>
    </div>
  );
}
