import type { EventResponse } from "@/types/event";
import { EventStatusBadge } from "./EventStatusBadge";
import { formatEventDate } from "@/lib/formatDate";

interface EventHeaderProps {
  event: EventResponse;
}

/** Title, venue, date/time, status badge — screen 3's header. */
export function EventHeader({ event }: EventHeaderProps) {
  return (
    <div className="flex flex-col gap-2 border-b border-[var(--border)] pb-4">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="font-[family-name:var(--font-display)] text-2xl font-semibold text-[var(--text-primary)]">
          {event.title}
        </h1>
        <EventStatusBadge status={event.status} />
      </div>
      <p className="label-mono">
        {event.venue.name} &middot; {event.venue.city}
      </p>
      <p className="label-mono">{formatEventDate(event.startsAt)}</p>
    </div>
  );
}
