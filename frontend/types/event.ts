/** Mirrors services/event's EventStatus enum. DRAFT is never shown on end-user screens. */
export type EventStatus = "DRAFT" | "ON_SALE" | "SOLD_OUT" | "CLOSED";

/** Mirrors services/event's EventSummaryResponse DTO — one card on the event-list screen. */
export interface EventSummaryResponse {
  id: number;
  title: string;
  venueName: string;
  city: string;
  startsAt: string;
  status: EventStatus;
  fromPrice: number | null;
}

/** Mirrors services/event's VenueDto. */
export interface VenueDto {
  id: number;
  name: string;
  address: string;
  city: string;
}

/** Mirrors services/event's SeatCategoryDto — a price tier, no section/seat internals. */
export interface SeatCategoryDto {
  id: number;
  name: string;
  price: number;
}

/**
 * Mirrors services/event's EventResponse DTO — event detail + venue + price tiers.
 * `bookable` mirrors the business rule "ON_SALE and startsAt in the future"; the UI
 * must not re-derive it from status + startsAt.
 */
export interface EventResponse {
  id: number;
  title: string;
  description: string;
  startsAt: string;
  status: EventStatus;
  bookable: boolean;
  venue: VenueDto;
  seatCategories: SeatCategoryDto[];
}

/**
 * Mirrors services/event's SeatDto — static seat layout only. ADR-0001: never gains an
 * availability field. Live availability comes from booking and is merged client-side by seatId.
 */
export interface SeatDto {
  seatId: number;
  section: string;
  row: string;
  number: number;
  seatCategory: SeatCategoryDto;
}

/** Mirrors services/event's SeatMapResponse DTO. */
export interface SeatMapResponse {
  eventId: number;
  venueId: number;
  seats: SeatDto[];
}

/** Query params accepted by GET /api/v1/events (see docs/user-flow.md screen 2). */
export interface EventFilterParams {
  city?: string;
  q?: string;
  from?: string;
  to?: string;
}
