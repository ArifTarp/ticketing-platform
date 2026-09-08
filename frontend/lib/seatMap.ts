import type { SeatAvailabilityResponse, SeatAvailabilityStatus } from "@/types/booking";

/** Visual state a `Seat` square renders as — screen 4's four states, no extras (per user-flow.md). */
export type SeatVisualStatus = "AVAILABLE" | "SELECTED" | "HELD" | "SOLD";

/** Indexes the availability read by seatId for O(1) lookup while merging with the layout read. */
export function toAvailabilityMap(rows: SeatAvailabilityResponse[]): Map<number, SeatAvailabilityStatus> {
  return new Map(rows.map((row) => [row.seatId, row.status]));
}

/**
 * Resolves one seat's visual status by merging the client's own selection with the live
 * availability read. A seatId absent from `availabilityBySeatId` is implicitly AVAILABLE
 * (business-rules.md's "Seat map contract"). The current session's own selection always wins over
 * a stale AVAILABLE/HELD read, since the hold call is what actually reserves the seat server-side.
 */
export function resolveSeatStatus(
  seatId: number,
  availabilityBySeatId: Map<number, SeatAvailabilityStatus>,
  selectedSeatIds: Set<number>,
): SeatVisualStatus {
  if (selectedSeatIds.has(seatId)) {
    return "SELECTED";
  }
  const status = availabilityBySeatId.get(seatId);
  if (status === "HELD" || status === "SOLD") {
    return status;
  }
  return "AVAILABLE";
}
