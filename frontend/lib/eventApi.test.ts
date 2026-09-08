import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchEvent, fetchEvents, fetchEventSeats } from "./eventApi";

describe("eventApi", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("fetchEvents calls GET /api/v1/events with no query string when no filters are given", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("[]", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    await fetchEvents();

    const [url, init] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/events");
    expect(init?.method).toBeUndefined();
  });

  it("fetchEvents serializes provided filters into the query string", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("[]", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    await fetchEvents({ city: "Istanbul", q: "jazz" });

    const [url] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/events?city=Istanbul&q=jazz");
  });

  it("fetchEvent calls GET /api/v1/events/{eventId}", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    await fetchEvent(42);

    const [url] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/events/42");
  });

  it("fetchEventSeats calls GET /api/v1/events/{eventId}/seats", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    await fetchEventSeats(42);

    const [url] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/events/42/seats");
  });

  it("rejects with the parsed 404 problem+json when the event doesn't exist", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ status: 404, title: "Not Found", detail: "Event 999 was not found" }), {
        status: 404,
        headers: { "Content-Type": "application/problem+json" },
      }),
    );

    await expect(fetchEvent(999)).rejects.toMatchObject({ status: 404 });
  });
});
