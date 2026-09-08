import type { EventStatus } from "@/types/event";

const STATUS_STYLES: Record<EventStatus, string> = {
  DRAFT: "bg-zinc-100 text-zinc-600",
  ON_SALE: "bg-emerald-100 text-emerald-700",
  SOLD_OUT: "bg-amber-100 text-amber-700",
  CLOSED: "bg-red-100 text-red-700",
};

const STATUS_LABELS: Record<EventStatus, string> = {
  DRAFT: "Draft",
  ON_SALE: "On sale",
  SOLD_OUT: "Sold out",
  CLOSED: "Closed",
};

interface EventStatusBadgeProps {
  status: EventStatus;
}

/** Visual badge mirroring the event status lifecycle exactly (DRAFT -> ON_SALE -> SOLD_OUT|CLOSED). */
export function EventStatusBadge({ status }: EventStatusBadgeProps) {
  return (
    <span
      className={`inline-flex shrink-0 items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_STYLES[status]}`}
    >
      {STATUS_LABELS[status]}
    </span>
  );
}
