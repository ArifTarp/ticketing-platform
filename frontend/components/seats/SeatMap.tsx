import { useMemo } from "react";
import type { SeatDto } from "@/types/event";
import type { SeatAvailabilityStatus } from "@/types/booking";
import { resolveSeatStatus } from "@/lib/seatMap";
import { Seat } from "./Seat";

interface SeatMapProps {
  seats: SeatDto[];
  availabilityBySeatId: Map<number, SeatAvailabilityStatus>;
  selectedSeatIds: Set<number>;
  onToggleSeat: (seat: SeatDto) => void;
  /** True once the current selection has already been turned into a held booking — freezes every
   *  seat so further clicks can't mutate selectedSeats/total without a matching holdSeats() call,
   *  which would let the displayed selection silently diverge from the booking actually held.
   *  Cleared again once the user starts a new selection (e.g. after the hold expires). */
  isLocked?: boolean;
}

interface SectionGroup {
  section: string;
  rows: { row: string; seats: SeatDto[] }[];
}

function groupBySection(seats: SeatDto[]): SectionGroup[] {
  const sectionMap = new Map<string, Map<string, SeatDto[]>>();

  for (const seat of seats) {
    const rowMap = sectionMap.get(seat.section) ?? new Map<string, SeatDto[]>();
    const rowSeats = rowMap.get(seat.row) ?? [];
    rowSeats.push(seat);
    rowMap.set(seat.row, rowSeats);
    sectionMap.set(seat.section, rowMap);
  }

  return Array.from(sectionMap.entries()).map(([section, rowMap]) => ({
    section,
    rows: Array.from(rowMap.entries())
      .map(([row, rowSeats]) => ({
        row,
        seats: [...rowSeats].sort((a, b) => a.number - b.number),
      }))
      .sort((a, b) => a.row.localeCompare(b.row)),
  }));
}

/**
 * Renders sections/rows of `Seat`s by merging the static layout (event service) with live
 * availability (booking service) client-side, per ADR-0001/business-rules.md's "Seat map contract".
 */
export function SeatMap({
  seats,
  availabilityBySeatId,
  selectedSeatIds,
  onToggleSeat,
  isLocked = false,
}: SeatMapProps) {
  const sections = useMemo(() => groupBySection(seats), [seats]);

  return (
    <div className="flex flex-col gap-6">
      <div className="label-mono rounded-[var(--radius-sm)] border border-[var(--border-strong)] bg-[var(--surface-sunken)] py-2 text-center">
        Stage
      </div>
      {sections.map((section) => (
        <div key={section.section} className="flex flex-col gap-2">
          <p className="label-mono">Section {section.section}</p>
          <div className="flex flex-col gap-1.5">
            {section.rows.map((row) => (
              <div key={row.row} className="flex items-center gap-2">
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
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
