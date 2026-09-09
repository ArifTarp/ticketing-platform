import type { SeatDto } from "@/types/event";
import { formatPrice } from "@/lib/formatPrice";
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
  return (
    <div className="panel sticky top-4 flex h-fit flex-col gap-4 p-4">
      <h2 className="font-[family-name:var(--font-display)] text-sm font-semibold text-[var(--text-primary)]">
        Your selection
      </h2>
      {selectedSeats.length === 0 ? (
        <p className="text-sm text-[var(--text-secondary)]">Click a seat to select it (up to 6).</p>
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
          Max 6 seats per booking.
        </p>
      )}
      <div className="flex items-center justify-between border-t border-[var(--border)] pt-3 text-sm font-semibold text-[var(--text-primary)]">
        <span>Total</span>
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
          {isCheckingOut ? "Proceeding…" : "Proceed to payment"}
        </button>
      ) : (
        <button
          type="button"
          onClick={onHoldSelectedSeats}
          disabled={selectedSeats.length === 0 || isHolding}
          className="btn btn-primary w-full"
        >
          {isHolding ? "Holding…" : "Hold selected seats"}
        </button>
      )}
    </div>
  );
}
