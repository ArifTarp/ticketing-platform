import type { BookingResponse } from "@/types/booking";
import { formatPrice } from "@/lib/formatPrice";
import { useLocale } from "@/lib/i18n/LocaleContext";
import { CountdownTimer } from "@/components/common/CountdownTimer";

interface BookingSummaryProps {
  booking: BookingResponse;
}

/** Seats + total for the current booking, reused by screens 5 and 6. */
export function BookingSummary({ booking }: BookingSummaryProps) {
  const { t } = useLocale();

  return (
    <div className="panel flex flex-col gap-4 p-5">
      <div className="flex items-center justify-between">
        <h2 className="label-mono">{t("checkout.bookingSummary")}</h2>
        {booking.status === "PENDING" && <CountdownTimer expiresAt={booking.expiresAt} />}
      </div>
      <ul className="flex flex-col gap-2 text-sm">
        {booking.items.map((item) => (
          <li
            key={item.seatId}
            className="flex items-center justify-between border-b border-[var(--border)] pb-2 last:border-b-0 last:pb-0"
          >
            <span className="value-mono text-[var(--text-secondary)]">
              {t("checkout.seat", { id: item.seatId })}
            </span>
            <span className="value-mono text-[var(--text-primary)]">{formatPrice(item.price)}</span>
          </li>
        ))}
      </ul>
      <div className="flex items-center justify-between border-t border-[var(--border-strong)] pt-3">
        <span className="label-mono">{t("checkout.total")}</span>
        <span className="value-mono text-base font-semibold text-[var(--text-primary)]">
          {formatPrice(booking.total)}
        </span>
      </div>
    </div>
  );
}
