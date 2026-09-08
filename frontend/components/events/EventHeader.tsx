import type { EventResponse } from "@/types/event";
import { EventStatusBadge } from "./EventStatusBadge";
import { formatEventDate } from "@/lib/formatDate";

interface EventHeaderProps {
  event: EventResponse;
}

/** Title, venue, date/time, status badge — screen 3's header. */
export function EventHeader({ event }: EventHeaderProps) {
  return (
    <div className="flex flex-col gap-2 border-b border-zinc-200 pb-4">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold text-zinc-900">{event.title}</h1>
        <EventStatusBadge status={event.status} />
      </div>
      <p className="text-sm text-zinc-500">
        {event.venue.name} · {event.venue.city}
      </p>
      <p className="text-sm text-zinc-500">{formatEventDate(event.startsAt)}</p>
    </div>
  );
}
