"use client";

import { useRouter } from "next/navigation";
import type { EventResponse } from "@/types/event";
import { useLocale } from "@/lib/i18n/LocaleContext";

interface SelectSeatsButtonProps {
  event: EventResponse;
}

function disabledReasonKey(event: EventResponse): string | null {
  if (event.bookable) {
    return null;
  }
  if (event.status === "SOLD_OUT") {
    return "eventDetail.reason.soldOut";
  }
  if (event.status === "CLOSED") {
    return "eventDetail.reason.closed";
  }
  if (new Date(event.startsAt).getTime() <= Date.now()) {
    return "eventDetail.reason.started";
  }
  return "eventDetail.reason.notOnSale";
}

/**
 * CTA to screen 4 (seat selection, Phase 12). Disabled (not hidden) when the event isn't
 * bookable — `event.bookable` already encodes "ON_SALE and startsAt in the future" server-side,
 * so the UI trusts it rather than re-deriving the rule.
 */
export function SelectSeatsButton({ event }: SelectSeatsButtonProps) {
  const router = useRouter();
  const { t } = useLocale();
  const reasonKey = disabledReasonKey(event);

  return (
    <div className="flex flex-col gap-1">
      <button
        type="button"
        disabled={!event.bookable}
        onClick={() => router.push(`/events/${event.id}/seats`)}
        className="btn btn-primary w-full"
      >
        {t("eventDetail.selectSeats")}
      </button>
      {reasonKey && <p className="label-mono">{t(reasonKey)}</p>}
    </div>
  );
}
