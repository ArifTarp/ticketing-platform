import { memo } from "react";
import type { SeatDto } from "@/types/event";
import type { SeatVisualStatus } from "@/lib/seatMap";

interface SeatProps {
  seat: SeatDto;
  status: SeatVisualStatus;
  onClick: (seat: SeatDto) => void;
  /** True once the current selection has already been turned into a held booking — freezes the
   *  seat map so further clicks can't diverge selectedSeats/total from the booking that was
   *  actually held (see SeatMap's `isLocked`). */
  isLocked?: boolean;
}

// Schematic seat-node states — kept as static Tailwind classes (no per-seat inline styles/shadows)
// so the seat map stays cheap to render even with many seats on screen at once.
const STATUS_CLASSES: Record<SeatVisualStatus, string> = {
  AVAILABLE:
    "border-[var(--border-strong)] bg-transparent text-[var(--text-secondary)] hover:border-[var(--accent)] hover:text-[var(--text-primary)] cursor-pointer",
  SELECTED:
    "border-[var(--accent)] bg-[var(--accent-soft)] text-[var(--accent-strong)] shadow-[0_0_0_3px_var(--accent-soft)] cursor-pointer",
  HELD:
    "border-[var(--status-pending)] bg-[var(--status-pending-soft)] text-[var(--status-pending)] cursor-not-allowed opacity-90",
  SOLD:
    "border-[var(--status-danger)]/60 bg-[var(--status-danger-soft)] text-[var(--text-muted)] cursor-not-allowed opacity-60",
};

/**
 * Single seat square; visual variant is a pure function of its merged SeatAvailability status.
 *
 * Wrapped in `memo` because `SeatMap` renders one of these per seat (up to ~80+ on screen at
 * once, more on a bigger venue) — without it, a single seat's status changing (e.g. selecting one
 * seat, or one availability row flipping HELD->SOLD after a poll) re-renders every other seat's
 * button too, since `SeatMap`'s own re-render otherwise re-invokes every child unconditionally.
 * `onClick` is intentionally excluded from the default shallow-prop comparison's concern here: the
 * only actually-changing identity across renders is `useSeatSelection().toggleSeat`, which is
 * already `useCallback`-stabilized, so the default `memo` comparator (shallow-equal on all props)
 * is sufficient without a custom comparator.
 */
export const Seat = memo(function Seat({ seat, status, onClick, isLocked = false }: SeatProps) {
  const isClickable = !isLocked && (status === "AVAILABLE" || status === "SELECTED");

  return (
    <button
      type="button"
      disabled={!isClickable}
      onClick={() => onClick(seat)}
      title={`${seat.section} ${seat.row}${seat.number} — ${seat.seatCategory.name}`}
      aria-pressed={status === "SELECTED"}
      className={`value-mono flex h-7 w-7 items-center justify-center rounded-[var(--radius-sm)] border text-[10px] font-medium transition-colors ${STATUS_CLASSES[status]}`}
    >
      {seat.number}
    </button>
  );
});
