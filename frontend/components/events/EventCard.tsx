import Link from "next/link";
import type { EventSummaryResponse } from "@/types/event";
import { EventStatusBadge } from "./EventStatusBadge";
import { formatPrice } from "@/lib/formatPrice";
import { formatEventDate } from "@/lib/formatDate";

interface EventCardProps {
  event: EventSummaryResponse;
}

/** Title, venue, date, "from $X" (lowest tier price), status badge — screen 2's grid item. */
export function EventCard({ event }: EventCardProps) {
  return (
    <Link
      href={`/events/${event.id}`}
      className="panel panel-interactive flex flex-col gap-3 p-4"
    >
      <div
        className="h-20 w-full rounded-[var(--radius-sm)] border border-[var(--border)]"
        style={{
          background:
            "linear-gradient(135deg, var(--accent-soft) 0%, var(--surface-hover) 55%, var(--surface) 100%)",
        }}
        aria-hidden
      />
      <div className="flex items-start justify-between gap-2">
        <h3 className="font-[family-name:var(--font-display)] text-base font-semibold text-[var(--text-primary)]">
          {event.title}
        </h3>
        <EventStatusBadge status={event.status} />
      </div>
      <p className="label-mono">
        {event.venueName} &middot; {event.city}
      </p>
      <p className="label-mono">{formatEventDate(event.startsAt)}</p>
      <p className="value-mono mt-auto text-sm font-medium text-[var(--text-primary)]">
        {event.fromPrice !== null ? `From ${formatPrice(event.fromPrice)}` : "Pricing TBA"}
      </p>
    </Link>
  );
}
