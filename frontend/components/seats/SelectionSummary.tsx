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
    <div className="flex flex-col gap-4 rounded-lg border border-zinc-200 bg-white p-4">
      <h2 className="text-sm font-semibold text-zinc-900">Your selection</h2>
      {selectedSeats.length === 0 ? (
        <p className="text-sm text-zinc-500">Click a seat to select it (up to 6).</p>
      ) : (
        <ul className="flex flex-col gap-1.5 text-sm">
          {selectedSeats.map((seat) => (
            <li key={seat.seatId} className="flex items-center justify-between">
              <span className="text-zinc-700">
                {seat.section} {seat.row}
                {seat.number}
              </span>
              <span className="text-zinc-900">{formatPrice(seat.seatCategory.price)}</span>
            </li>
          ))}
        </ul>
      )}
      {maxSeatsReached && (
        <p className="text-xs text-amber-600">Max 6 seats per booking.</p>
      )}
      <div className="flex items-center justify-between border-t border-zinc-200 pt-3 text-sm font-semibold">
        <span>Total</span>
        <span>{formatPrice(total)}</span>
      </div>
      <FormError message={holdError} />
      {isHeld ? (
        <button
          type="button"
          onClick={onProceedToPayment}
          disabled={isCheckingOut}
          className="w-full rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-700 disabled:cursor-not-allowed disabled:bg-zinc-300"
        >
          {isCheckingOut ? "Proceeding…" : "Proceed to payment"}
        </button>
      ) : (
        <button
          type="button"
          onClick={onHoldSelectedSeats}
          disabled={selectedSeats.length === 0 || isHolding}
          className="w-full rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-700 disabled:cursor-not-allowed disabled:bg-zinc-300"
        >
          {isHolding ? "Holding…" : "Hold selected seats"}
        </button>
      )}
    </div>
  );
}
