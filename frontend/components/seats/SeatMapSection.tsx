import { memo } from "react";
import type { SeatDto } from "@/types/event";
import type { SeatAvailabilityStatus } from "@/types/booking";
import { resolveSeatStatus } from "@/lib/seatMap";
import { Seat } from "./Seat";

export interface SeatMapSectionProps {
  rows: { row: string; seats: SeatDto[] }[];
  /** Degrees this section is rotated away from dead-center-facing-the-stage — negative fans left,
   *  positive fans right. 0 for the middle section(s) (task 8: "arc/sector visual" via CSS
   *  transforms, no new per-seat coordinate data from the backend). */
  rotationDeg: number;
  availabilityBySeatId: Map<number, SeatAvailabilityStatus>;
  selectedSeatIds: Set<number>;
  onToggleSeat: (seat: SeatDto) => void;
  isLocked: boolean;
  sectionLabel: string;
}

/**
 * One section's rows, rotated as a block around a transform origin near the stage to read as a
 * shallow arc/sector around "SAHNE" rather than a flat grid — see `SeatMap`'s layout comment.
 * Split out of `SeatMap` and `memo`-ized (same rationale as `Seat`, business-rules.md's perf
 * finding): with 150-250 seats across several sections, re-rendering only the section(s) whose
 * own props actually changed (not every section on any unrelated page state change) matters.
 * Each row within the section is staggered by a small per-row horizontal offset — increasing with
 * distance from the front row — to read as a gentle curve rather than a literal per-seat arc.
 */
export const SeatMapSection = memo(function SeatMapSection({
  rows,
  rotationDeg,
  availabilityBySeatId,
  selectedSeatIds,
  onToggleSeat,
  isLocked,
  sectionLabel,
}: SeatMapSectionProps) {
  return (
    <div
      className="flex flex-col gap-2"
      style={{
        transform: `rotate(${rotationDeg}deg)`,
        transformOrigin: "bottom center",
      }}
    >
      <p className="label-mono text-center">{sectionLabel}</p>
      <div className="flex flex-col gap-1.5">
        {rows.map((row, rowIndex) => {
          // Rows further from the stage (higher rowIndex) fan out a little more — a cheap
          // "curved rows" illusion via margin alone, no per-seat coordinates.
          const fanOffsetPx = rowIndex * (rotationDeg === 0 ? 0 : Math.sign(rotationDeg) * 3);
          return (
            <div
              key={row.row}
              className="flex items-center gap-2"
              style={{ marginLeft: fanOffsetPx > 0 ? fanOffsetPx : 0, marginRight: fanOffsetPx < 0 ? -fanOffsetPx : 0 }}
            >
              <span className="value-mono w-6 text-xs text-[var(--text-muted)]">{row.row}</span>
              <div className="flex flex-wrap gap-1.5">
                {row.seats.map((seat) => (
                  <Seat
                    key={seat.seatId}
                    seat={seat}
                    status={resolveSeatStatus(seat.seatId, availabilityBySeatId, selectedSeatIds)}
                    onClick={onToggleSeat}
                    isLocked={isLocked}
                  />
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
});
