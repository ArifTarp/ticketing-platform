import Link from "next/link";
import type { BookingResponse } from "@/types/booking";
import { formatPrice } from "@/lib/formatPrice";
import { useLocale } from "@/lib/i18n/LocaleContext";

interface BookingSuccessPanelProps {
  booking: BookingResponse;
}

/** Success view for a CONFIRMED booking — screen 6. */
export function BookingSuccessPanel({ booking }: BookingSuccessPanelProps) {
  const { t } = useLocale();

  return (
    <div
      className="panel booking-success-scale-in flex flex-col items-center gap-4 px-6 py-10 text-center"
      style={{ borderColor: "var(--status-live)", boxShadow: "0 0 0 1px var(--status-live-soft), 0 24px 48px -24px rgba(52, 211, 153, 0.35)" }}
    >
      <style>{`
        @keyframes booking-success-scale-in {
          0% { opacity: 0; transform: scale(0.94); }
          100% { opacity: 1; transform: scale(1); }
        }
        .booking-success-scale-in {
          animation: booking-success-scale-in 320ms ease-out;
        }
      `}</style>
      <div
        className="flex h-10 w-10 items-center justify-center rounded-full text-base"
        style={{ backgroundColor: "var(--status-live-soft)", color: "var(--status-live)" }}
      >
        ✓
      </div>
      <span
        className="label-mono rounded-full px-2.5 py-1"
        style={{ color: "var(--status-live)", backgroundColor: "var(--status-live-soft)" }}
      >
        {t("confirm.confirmedBadge")}
      </span>
      <h1 className="font-[family-name:var(--font-display)] text-lg font-semibold text-[var(--text-primary)]">
        {t("confirm.bookingConfirmed")}
      </h1>
      <ul className="flex flex-col gap-1 text-sm">
        {booking.items.map((item) => (
          <li key={item.seatId} className="value-mono text-[var(--text-secondary)]">
            {t("checkout.seat", { id: item.seatId })}
          </li>
        ))}
      </ul>
      <p className="value-mono text-sm font-medium text-[var(--text-primary)]">
        {t("confirm.totalLabel", { price: formatPrice(booking.total) })}
      </p>
      <Link href="/tickets" className="btn btn-primary">
        {t("confirm.viewMyTickets")}
      </Link>
    </div>
  );
}
