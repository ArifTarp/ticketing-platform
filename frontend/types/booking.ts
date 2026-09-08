/** Mirrors services/booking's BookingStatus enum. A booking never leaves a terminal state. */
export type BookingStatus = "PENDING" | "CONFIRMED" | "CANCELLED" | "EXPIRED";

/** Mirrors services/booking's SeatAvailabilityStatus enum (SeatAvailability.status). */
export type SeatAvailabilityStatus = "AVAILABLE" | "HELD" | "SOLD";

/** Mirrors services/booking's BookingItemDto — one held/sold seat, price snapshot at hold time. */
export interface BookingItemDto {
  seatId: number;
  price: number;
}

/** Mirrors services/booking's BookingResponse DTO — booking detail, screens 4-7's data source. */
export interface BookingResponse {
  id: number;
  userId: number;
  eventId: number;
  status: BookingStatus;
  total: number;
  createdAt: string;
  expiresAt: string;
  items: BookingItemDto[];
}

/** Mirrors services/booking's HoldSeatRequest DTO — price is client-supplied, see business-rules.md. */
export interface HoldSeatRequest {
  seatId: number;
  price: number;
}

/** Mirrors services/booking's HoldBookingRequest DTO — body of POST /api/v1/bookings/hold. */
export interface HoldBookingRequest {
  userId: number;
  eventId: number;
  seats: HoldSeatRequest[];
}

/** Mirrors services/booking's SeatAvailabilityResponse DTO — one row of the live-availability read. */
export interface SeatAvailabilityResponse {
  seatId: number;
  status: SeatAvailabilityStatus;
}
