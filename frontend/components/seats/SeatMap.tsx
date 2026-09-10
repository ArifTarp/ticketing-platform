import { useMemo } from "react";
import type { SeatDto } from "@/types/event";
import type { SeatAvailabilityStatus } from "@/types/booking";
import { useLocale } from "@/lib/i18n/LocaleContext";
import { SeatMapSection } from "./SeatMapSection";

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

/** Degrees each section fans out per step away from center — a tasteful, approximate arc, not a
 *  literal per-seat coordinate layout (no new coordinate data exists on the backend, task 8). */
const ROTATION_STEP_DEG = 5;
const MAX_ROTATION_DEG = 15;

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

/** Section index -> rotation in degrees, fanning symmetrically around the middle section(s). */
function rotationForIndex(index: number, count: number): number {
  if (count <= 1) {
    return 0;
  }
  const center = (count - 1) / 2;
  const offset = index - center;
  return Math.max(-MAX_ROTATION_DEG, Math.min(MAX_ROTATION_DEG, offset * ROTATION_STEP_DEG));
}

/**
 * Renders sections/rows of `Seat`s by merging the static layout (event service) with live
 * availability (booking service) client-side, per ADR-0001/business-rules.md's "Seat map contract".
 *
 * Layout (task 8): sections are arranged as a shallow arc/sector around a central "SAHNE" (stage)
 * element via CSS `rotate()` on each section block (see `SeatMapSection`), rather than one flat
 * grid — an approximate, CSS-only fan, since no per-seat coordinate data exists on the backend.
 * Kept performant for 150-250 seats: `Seat` and `SeatMapSection` are both `memo`-ized, and this
 * component still only re-groups seats into sections when the `seats` array itself changes.
 */
export function SeatMap({
  seats,
  availabilityBySeatId,
  selectedSeatIds,
  onToggleSeat,
  isLocked = false,
}: SeatMapProps) {
  const { t } = useLocale();
  const sections = useMemo(() => groupBySection(seats), [seats]);

  return (
    <div className="flex flex-col gap-8">
      <div className="label-mono mx-auto w-2/3 rounded-[var(--radius-sm)] border border-[var(--border-strong)] bg-[var(--surface-sunken)] py-2 text-center">
        {t("seats.stage")}
      </div>
      <div className="flex flex-wrap items-end justify-center gap-x-8 gap-y-6" style={{ perspective: "1200px" }}>
        {sections.map((section, index) => (
          <SeatMapSection
            key={section.section}
            rows={section.rows}
            rotationDeg={rotationForIndex(index, sections.length)}
            availabilityBySeatId={availabilityBySeatId}
            selectedSeatIds={selectedSeatIds}
            onToggleSeat={onToggleSeat}
            isLocked={isLocked}
            sectionLabel={t("seats.section", { name: section.section })}
          />
        ))}
      </div>
    </div>
  );
}
