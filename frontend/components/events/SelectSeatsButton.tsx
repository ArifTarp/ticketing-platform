"use client";

import { useRouter } from "next/navigation";
import type { EventResponse } from "@/types/event";

interface SelectSeatsButtonProps {
  event: EventResponse;
}

function disabledReason(event: EventResponse): string | null {
  if (event.bookable) {
    return null;
  }
  if (event.status === "SOLD_OUT") {
    return "This event is sold out.";
  }
  if (event.status === "CLOSED") {
    return "Ticket sales are closed for this event.";
  }
  if (new Date(event.startsAt).getTime() <= Date.now()) {
    return "This event has already started.";
  }
  return "This event is not on sale yet.";
}

/**
 * CTA to screen 4 (seat selection, Phase 12). Disabled (not hidden) when the event isn't
 * bookable — `event.bookable` already encodes "ON_SALE and startsAt in the future" server-side,
 * so the UI trusts it rather than re-deriving the rule.
 */
export function SelectSeatsButton({ event }: SelectSeatsButtonProps) {
  const router = useRouter();
  const reason = disabledReason(event);

  return (
    <div className="flex flex-col gap-1">
      <button
        type="button"
        disabled={!event.bookable}
        onClick={() => router.push(`/events/${event.id}/seats`)}
        className="btn btn-primary w-full"
      >
        Select seats
      </button>
      {reason && <p className="label-mono">{reason}</p>}
    </div>
  );
}
