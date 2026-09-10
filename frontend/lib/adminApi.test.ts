import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  createEvent,
  createSeatCategories,
  createVenue,
  deleteEvent,
  fetchAdminEvents,
  updateEvent,
} from "./adminApi";

describe("adminApi", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("fetchAdminEvents calls GET /api/v1/admin/events with no query string when no filters are given", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("[]", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    await fetchAdminEvents();

    const [url] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/admin/events");
  });

  it("fetchAdminEvents serializes provided filters into the query string", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("[]", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    await fetchAdminEvents({ city: "Istanbul" });

    const [url] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/admin/events?city=Istanbul");
  });

  it("createVenue POSTs to /api/v1/venues", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ id: 1, name: "Arena", address: "1 Main St", city: "Istanbul" }), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await createVenue({ name: "Arena", address: "1 Main St", city: "Istanbul" });

    const [url, init] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/venues");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(init?.body as string)).toEqual({
      name: "Arena",
      address: "1 Main St",
      city: "Istanbul",
    });
  });

  it("createEvent POSTs to /api/v1/events", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("{}", { status: 201, headers: { "Content-Type": "application/json" } }),
    );

    await createEvent({
      venueId: 1,
      title: "Rock Night",
      description: "",
      startsAt: "2026-10-01T20:00:00Z",
      status: "DRAFT",
    });

    const [url, init] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/events");
    expect(init?.method).toBe("POST");
  });

  it("createSeatCategories POSTs a raw array to /api/v1/events/{eventId}/seat-categories", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("[]", { status: 201, headers: { "Content-Type": "application/json" } }),
    );

    await createSeatCategories(42, [{ name: "VIP", price: 120, section: "A" }]);

    const [url, init] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/events/42/seat-categories");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(init?.body as string)).toEqual([{ name: "VIP", price: 120, section: "A" }]);
  });

  it("updateEvent PUTs to /api/v1/events/{eventId}", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    await updateEvent(42, {
      title: "Rock Night (rescheduled)",
      description: "",
      startsAt: "2026-10-02T20:00:00Z",
      status: "ON_SALE",
      imageUrl: "https://example.com/poster.jpg",
    });

    const [url, init] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/events/42");
    expect(init?.method).toBe("PUT");
    expect(JSON.parse(init?.body as string)).toEqual({
      title: "Rock Night (rescheduled)",
      description: "",
      startsAt: "2026-10-02T20:00:00Z",
      status: "ON_SALE",
      imageUrl: "https://example.com/poster.jpg",
    });
  });

  it("deleteEvent DELETEs to /api/v1/events/{eventId}", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }));

    await deleteEvent(42);

    const [url, init] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/events/42");
    expect(init?.method).toBe("DELETE");
  });

  it("rejects deleteEvent with the parsed 404 problem+json when the event doesn't exist", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ status: 404, title: "Not Found", detail: "No such event" }), {
        status: 404,
        headers: { "Content-Type": "application/problem+json" },
      }),
    );

    await expect(deleteEvent(999)).rejects.toMatchObject({ status: 404 });
  });

  it("rejects with the parsed 409 problem+json on a duplicate seat-category name/section", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({ status: 409, title: "Conflict", detail: "Seat category already exists" }),
        { status: 409, headers: { "Content-Type": "application/problem+json" } },
      ),
    );

    await expect(
      createSeatCategories(42, [{ name: "VIP", price: 120, section: "A" }]),
    ).rejects.toMatchObject({ status: 409 });
  });
});
