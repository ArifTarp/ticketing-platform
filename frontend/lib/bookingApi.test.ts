import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  checkout,
  fetchMyBookings,
  fetchPendingBooking,
  fetchSeatAvailability,
  getBooking,
  holdSeats,
} from "./bookingApi";

describe("bookingApi", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("holdSeats POSTs to /api/v1/bookings/hold with the request body", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    await holdSeats({ userId: 1, eventId: 2, seats: [{ seatId: 3, price: 120 }] });

    const [url, init] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/bookings/hold");
    expect(init?.method).toBe("POST");
    expect(init?.body).toBe(JSON.stringify({ userId: 1, eventId: 2, seats: [{ seatId: 3, price: 120 }] }));
  });

  it("checkout POSTs to /api/v1/bookings/{id}/checkout with no body", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    await checkout(42);

    const [url, init] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/bookings/42/checkout");
    expect(init?.method).toBe("POST");
    expect(init?.body).toBeUndefined();
  });

  it("getBooking calls GET /api/v1/bookings/{id}", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    await getBooking(42);

    const [url] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/bookings/42");
  });

  it("fetchMyBookings serializes userId and optional status", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("[]", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    await fetchMyBookings(7, "CONFIRMED");

    const [url] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/bookings?userId=7&status=CONFIRMED");
  });

  it("fetchPendingBooking queries userId+eventId+status=PENDING and returns the first result", async () => {
    const booking = {
      id: 5,
      userId: 7,
      eventId: 9,
      status: "PENDING",
      total: 120,
      createdAt: "2026-01-01T00:00:00Z",
      expiresAt: "2026-01-01T00:10:00Z",
      items: [{ seatId: 1, price: 120 }],
    };
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify([booking]), { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    const result = await fetchPendingBooking(7, 9);

    const [url] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/bookings?userId=7&eventId=9&status=PENDING");
    expect(result).toEqual(booking);
  });

  it("fetchPendingBooking returns null when there's no in-progress hold", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("[]", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    const result = await fetchPendingBooking(7, 9);

    expect(result).toBeNull();
  });

  it("fetchSeatAvailability calls GET /api/v1/bookings/availability?eventId=", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("[]", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    await fetchSeatAvailability(9);

    const [url] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/bookings/availability?eventId=9");
  });

  it("rejects with the parsed 409 problem+json on a seat conflict", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({ status: 409, title: "Conflict", detail: "Seat 3 is already held" }),
        { status: 409, headers: { "Content-Type": "application/problem+json" } },
      ),
    );

    await expect(holdSeats({ userId: 1, eventId: 2, seats: [{ seatId: 3, price: 120 }] })).rejects.toMatchObject({
      status: 409,
    });
  });
});
