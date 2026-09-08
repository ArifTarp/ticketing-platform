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
      className="flex flex-col gap-2 rounded-lg border border-zinc-200 bg-white p-4 shadow-sm transition hover:shadow-md"
    >
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-base font-semibold text-zinc-900">{event.title}</h3>
        <EventStatusBadge status={event.status} />
      </div>
      <p className="text-sm text-zinc-500">
        {event.venueName} · {event.city}
      </p>
      <p className="text-sm text-zinc-500">{formatEventDate(event.startsAt)}</p>
      <p className="mt-auto text-sm font-medium text-zinc-900">
        {event.fromPrice !== null ? `From ${formatPrice(event.fromPrice)}` : "Pricing TBA"}
      </p>
    </Link>
  );
}
