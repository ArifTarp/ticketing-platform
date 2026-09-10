import type { SeatDto } from "@/types/event";
import { formatPrice } from "@/lib/formatPrice";
import { useLocale } from "@/lib/i18n/LocaleContext";
import { FormError } from "@/components/common/FormError";

interface SelectionSummaryProps {
  selectedSeats: SeatDto[];
  total: number;
  isHolding: boolean;
  holdError: string | null;
  maxSeatsReached: boolean;
  /** True once the current selection has already been turned into a PENDING booking. */
  isHeld: boolean;
  isCheckingOut: boolean;
  onHoldSelectedSeats: () => void;
  onProceedToPayment: () => void;
}

/** Selected seats, running total, and the screen 4 CTAs — hold, then proceed to payment. */
export function SelectionSummary({
  selectedSeats,
  total,
  isHolding,
  holdError,
  maxSeatsReached,
  isHeld,
  isCheckingOut,
  onHoldSelectedSeats,
  onProceedToPayment,
}: SelectionSummaryProps) {
  const { t } = useLocale();

  return (
    <div className="panel sticky top-4 flex h-fit flex-col gap-4 p-4">
      <h2 className="font-[family-name:var(--font-display)] text-sm font-semibold text-[var(--text-primary)]">
        {t("seats.summary.title")}
      </h2>
      {selectedSeats.length === 0 ? (
        <p className="text-sm text-[var(--text-secondary)]">{t("seats.summary.prompt")}</p>
      ) : (
        <ul className="flex flex-col gap-1.5 text-sm">
          {selectedSeats.map((seat) => (
            <li key={seat.seatId} className="flex items-center justify-between">
              <span className="value-mono text-[var(--text-secondary)]">
                {seat.section} {seat.row}
                {seat.number}
              </span>
              <span className="value-mono text-[var(--text-primary)]">
                {formatPrice(seat.seatCategory.price)}
              </span>
            </li>
          ))}
        </ul>
      )}
      {maxSeatsReached && (
        <p className="label-mono" style={{ color: "var(--status-pending)" }}>
          {t("seats.summary.maxReached")}
        </p>
      )}
      <div className="flex items-center justify-between border-t border-[var(--border)] pt-3 text-sm font-semibold text-[var(--text-primary)]">
        <span>{t("seats.summary.total")}</span>
        <span className="value-mono">{formatPrice(total)}</span>
      </div>
      <FormError message={holdError} />
      {isHeld ? (
        <button
          type="button"
          onClick={onProceedToPayment}
          disabled={isCheckingOut}
          className="btn btn-primary w-full"
        >
          {isCheckingOut ? t("seats.summary.proceeding") : t("seats.summary.proceedToPayment")}
        </button>
      ) : (
        <button
          type="button"
          onClick={onHoldSelectedSeats}
          disabled={selectedSeats.length === 0 || isHolding}
          className="btn btn-primary w-full"
        >
          {isHolding ? t("seats.summary.holding") : t("seats.summary.holdSelected")}
        </button>
      )}
    </div>
  );
}
