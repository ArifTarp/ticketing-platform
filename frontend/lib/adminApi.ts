import { apiFetch } from "./apiClient";
import type {
  CreateEventRequest,
  CreateSeatCategoryRequest,
  CreateVenueRequest,
  EventFilterParams,
  EventResponse,
  EventSummaryResponse,
  SeatCategoryDto,
  UpdateEventRequest,
  VenueDto,
} from "@/types/event";

function toQueryString(filters: EventFilterParams): string {
  const search = new URLSearchParams();
  if (filters.city) search.set("city", filters.city);
  if (filters.q) search.set("q", filters.q);
  if (filters.from) search.set("from", filters.from);
  if (filters.to) search.set("to", filters.to);
  const query = search.toString();
  return query ? `?${query}` : "";
}

/**
 * GET /api/v1/admin/events?city=&q=&from=&to= — ADMIN-only (gateway-enforced), every event status
 * (DRAFT/ON_SALE/SOLD_OUT/CLOSED). Backs the admin events table (docs/user-flow.md screen 8);
 * distinct from the public fetchEvents(), which stays ON_SALE-only.
 */
export function fetchAdminEvents(
  filters: EventFilterParams = {},
): Promise<EventSummaryResponse[]> {
  return apiFetch<EventSummaryResponse[]>(`/api/v1/admin/events${toQueryString(filters)}`);
}

/** POST /api/v1/venues — ADMIN-only. Used both by VenueSelect's inline "+ new venue" mini-form and the standalone "+ Create venue" action. */
export function createVenue(request: CreateVenueRequest): Promise<VenueDto> {
  return apiFetch<VenueDto>("/api/v1/venues", {
    method: "POST",
    body: request,
  });
}

/** POST /api/v1/events — ADMIN-only. `status` defaults to DRAFT server-side when omitted. */
export function createEvent(request: CreateEventRequest): Promise<EventResponse> {
  return apiFetch<EventResponse>("/api/v1/events", {
    method: "POST",
    body: request,
  });
}

/** PUT /api/v1/events/{eventId} — ADMIN-only. Returns the updated event resource. */
export function updateEvent(
  eventId: string | number,
  request: UpdateEventRequest,
): Promise<EventResponse> {
  return apiFetch<EventResponse>(`/api/v1/events/${eventId}`, {
    method: "PUT",
    body: request,
  });
}

/** DELETE /api/v1/events/{eventId} — ADMIN-only. 204 on success, 404 if not found. */
export function deleteEvent(eventId: string | number): Promise<void> {
  return apiFetch<void>(`/api/v1/events/${eventId}`, {
    method: "DELETE",
  });
}

/**
 * POST /api/v1/events/{eventId}/seat-categories — ADMIN-only. Body is a raw JSON array (not
 * wrapped in an object). 409 (ApiRequestError) if a name/section is already used by this event.
 */
export function createSeatCategories(
  eventId: string | number,
  requests: CreateSeatCategoryRequest[],
): Promise<SeatCategoryDto[]> {
  return apiFetch<SeatCategoryDto[]>(`/api/v1/events/${eventId}/seat-categories`, {
    method: "POST",
    body: requests,
  });
}
