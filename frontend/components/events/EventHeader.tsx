import type { EventResponse } from "@/types/event";
import { EventStatusBadge } from "./EventStatusBadge";
import { formatEventDate } from "@/lib/formatDate";
import { useLocale } from "@/lib/i18n/LocaleContext";

interface EventHeaderProps {
  event: EventResponse;
}

/** Title, venue, date/time, status badge, and cover image — screen 3's header. */
export function EventHeader({ event }: EventHeaderProps) {
  const { locale } = useLocale();

  return (
    <div className="flex flex-col gap-4">
      {event.imageUrl ? (
        // eslint-disable-next-line @next/next/no-img-element -- remote, admin-supplied URLs.
        <img
          src={event.imageUrl}
          alt=""
          className="h-48 w-full rounded-[var(--radius-md)] border border-[var(--border)] object-cover sm:h-64"
        />
      ) : (
        <div
          className="h-48 w-full rounded-[var(--radius-md)] border border-[var(--border)] sm:h-64"
          style={{
            background:
              "linear-gradient(135deg, var(--accent-soft) 0%, var(--surface-hover) 55%, var(--surface) 100%)",
          }}
          aria-hidden
        />
      )}
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
        <p className="label-mono">{formatEventDate(event.startsAt, locale)}</p>
      </div>
    </div>
  );
}
