import { apiFetch } from "./apiClient";
import type {
  EventFilterParams,
  EventResponse,
  EventSummaryResponse,
  SeatMapResponse,
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

/** GET /api/v1/events?city=&q=&from=&to= — only ON_SALE events, per the event service contract. */
export function fetchEvents(
  filters: EventFilterParams = {},
): Promise<EventSummaryResponse[]> {
  return apiFetch<EventSummaryResponse[]>(`/api/v1/events${toQueryString(filters)}`);
}

/** GET /api/v1/events/{eventId} — detail + venue + price tiers, any status. */
export function fetchEvent(eventId: string | number): Promise<EventResponse> {
  return apiFetch<EventResponse>(`/api/v1/events/${eventId}`);
}

/**
 * GET /api/v1/events/{eventId}/seats — static seat layout only, no availability (ADR-0001).
 * Not needed until screen 4 (Phase 12); scaffolded here since it's a trivial addition to this file.
 */
export function fetchEventSeats(eventId: string | number): Promise<SeatMapResponse> {
  return apiFetch<SeatMapResponse>(`/api/v1/events/${eventId}/seats`);
}
