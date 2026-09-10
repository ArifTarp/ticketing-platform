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
  /** Landing from a parallel event-service change; null/undefined for events seeded before the
   *  migration backfill — callers must render a fallback rather than assume it's always present. */
  imageUrl?: string | null;
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
  imageUrl?: string | null;
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

/** POST /api/v1/venues body — mirrors services/event's CreateVenueRequest (Phase 14, admin-only). */
export interface CreateVenueRequest {
  name: string;
  address: string;
  city: string;
}

/**
 * POST /api/v1/events body — mirrors services/event's CreateEventRequest (Phase 14, admin-only).
 * `status` is optional and defaults to `DRAFT` server-side when omitted.
 */
export interface CreateEventRequest {
  venueId: number;
  title: string;
  description: string;
  startsAt: string;
  status?: EventStatus;
}

/**
 * PUT /api/v1/events/{eventId} body — mirrors services/event's UpdateEventRequest (Phase 14,
 * admin-only). Same shape as CreateEventRequest minus `venueId` (the venue isn't editable),
 * plus `imageUrl`.
 */
export interface UpdateEventRequest {
  title: string;
  description: string;
  startsAt: string;
  status: EventStatus;
  imageUrl: string | null;
}

/**
 * One item of POST /api/v1/events/{eventId}/seat-categories's request body (a raw JSON array of
 * these) — mirrors services/event's CreateSeatCategoryRequest. `section` is how the tier attaches
 * to physical seats; no `id` since these are always new rows (the endpoint only adds, never edits).
 */
export interface CreateSeatCategoryRequest {
  name: string;
  price: number;
  section: string;
}
