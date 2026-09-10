import { apiFetch } from "./apiClient";
import type {
  BookingResponse,
  BookingStatus,
  HoldBookingRequest,
  SeatAvailabilityResponse,
} from "@/types/booking";

/**
 * POST /api/v1/bookings/hold — creates a new PENDING booking holding up to
 * MAX_SEATS_PER_BOOKING seats. 409 (ApiRequestError) if any seat is already locked/held/sold.
 */
export function holdSeats(request: HoldBookingRequest): Promise<BookingResponse> {
  return apiFetch<BookingResponse>("/api/v1/bookings/hold", {
    method: "POST",
    body: request,
  });
}

/**
 * POST /api/v1/bookings/{bookingId}/checkout — the single, sole trigger for the checkout saga
 * (business-rules.md). Returns the booking still PENDING; the caller polls getBooking() for the
 * eventual CONFIRMED/CANCELLED/EXPIRED outcome.
 */
export function checkout(bookingId: string | number): Promise<BookingResponse> {
  return apiFetch<BookingResponse>(`/api/v1/bookings/${bookingId}/checkout`, {
    method: "POST",
  });
}

/** GET /api/v1/bookings/{bookingId} — booking detail (screens 4-6's shared data source). */
export function getBooking(bookingId: string | number): Promise<BookingResponse> {
  return apiFetch<BookingResponse>(`/api/v1/bookings/${bookingId}`);
}

/**
 * GET /api/v1/bookings?userId=&status= — a user's bookings, newest first. `userId` must be
 * resolved from the decoded JWT's `sub` claim client-side (services/booking/CLAUDE.md's
 * "userId=me note" — the backend does not accept the literal string "me").
 */
export function fetchMyBookings(userId: string | number, status?: BookingStatus): Promise<BookingResponse[]> {
  const search = new URLSearchParams({ userId: String(userId) });
  if (status) search.set("status", status);
  return apiFetch<BookingResponse[]>(`/api/v1/bookings?${search.toString()}`);
}

/**
 * GET /api/v1/bookings?userId=&eventId=&status=PENDING — the seat-selection screen's "resume my
 * in-progress hold on page refresh" query (services/booking/CLAUDE.md's append-not-duplicate
 * invariant guarantees at most one `(userId, eventId, PENDING)` booking, so this always resolves
 * to at most one result). Returns null when there's no in-progress hold to resume.
 */
export async function fetchPendingBooking(
  userId: string | number,
  eventId: string | number,
): Promise<BookingResponse | null> {
  const search = new URLSearchParams({
    userId: String(userId),
    eventId: String(eventId),
    status: "PENDING" satisfies BookingStatus,
  });
  const results = await apiFetch<BookingResponse[]>(`/api/v1/bookings?${search.toString()}`);
  return results[0] ?? null;
}

/**
 * GET /api/v1/bookings/availability?eventId= — live per-seat status for one event. Merge
 * client-side with fetchEventSeats() by seatId (business-rules.md's "Seat map contract"); a
 * seatId absent from this list is implicitly AVAILABLE.
 */
export function fetchSeatAvailability(eventId: string | number): Promise<SeatAvailabilityResponse[]> {
  return apiFetch<SeatAvailabilityResponse[]>(`/api/v1/bookings/availability?eventId=${eventId}`);
}
