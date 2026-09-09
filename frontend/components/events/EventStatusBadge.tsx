import type { EventStatus } from "@/types/event";

const STATUS_COLOR: Record<EventStatus, string> = {
  DRAFT: "var(--status-neutral)",
  ON_SALE: "var(--status-live)",
  SOLD_OUT: "var(--status-pending)",
  CLOSED: "var(--status-danger)",
};

const STATUS_SOFT: Record<EventStatus, string> = {
  DRAFT: "var(--status-neutral-soft)",
  ON_SALE: "var(--status-live-soft)",
  SOLD_OUT: "var(--status-pending-soft)",
  CLOSED: "var(--status-danger-soft)",
};

const STATUS_LABELS: Record<EventStatus, string> = {
  DRAFT: "Draft",
  ON_SALE: "Live",
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
      className="label-mono inline-flex shrink-0 items-center gap-1.5 rounded-full px-2.5 py-1"
      style={{ color: STATUS_COLOR[status], backgroundColor: STATUS_SOFT[status] }}
    >
      <span
        className={`status-dot ${status === "ON_SALE" ? "status-dot-pulse" : ""}`}
        style={{ backgroundColor: STATUS_COLOR[status] }}
        aria-hidden
      />
      {STATUS_LABELS[status]}
    </span>
  );
}
