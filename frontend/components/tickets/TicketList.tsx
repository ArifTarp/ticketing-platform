"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { fetchMyBookings } from "@/lib/bookingApi";
import { fetchEvent } from "@/lib/eventApi";
import { ApiRequestError } from "@/lib/apiClient";
import { useAuth } from "@/context/SessionProvider";
import type { BookingResponse } from "@/types/booking";
import type { EventResponse } from "@/types/event";
import { EmptyState } from "@/components/common/EmptyState";
import { TicketCard } from "./TicketCard";
import { TicketCardSkeleton } from "./TicketCardSkeleton";

const SKELETON_COUNT = 3;

/** Fetch + list container for screen 7 — only CONFIRMED bookings, each merged with its event. */
export function TicketList() {
  const { user } = useAuth();
  const [bookings, setBookings] = useState<BookingResponse[] | null>(null);
  const [events, setEvents] = useState<Record<number, EventResponse | null>>({});
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [reloadToken, setReloadToken] = useState(0);

  const loadBookings = useCallback(async (userId: string) => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchMyBookings(userId, "CONFIRMED");
      setBookings(result);

      const uniqueEventIds = Array.from(new Set(result.map((booking) => booking.eventId)));
      uniqueEventIds.forEach((eventId) => {
        fetchEvent(eventId)
          .then((event) => setEvents((prev) => ({ ...prev, [eventId]: event })))
          .catch(() => setEvents((prev) => ({ ...prev, [eventId]: null })));
      });
    } catch (err) {
      setBookings(null);
      setError(err instanceof ApiRequestError ? err.detail : "Failed to load your tickets.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!user) return;
    loadBookings(user.userId);
  }, [user, reloadToken, loadBookings]);

  if (isLoading) {
    return (
      <div className="flex flex-col gap-4">
        {Array.from({ length: SKELETON_COUNT }).map((_, index) => (
          <TicketCardSkeleton key={index} />
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <EmptyState
        title="Couldn't load your tickets"
        description={error}
        action={
          <button
            type="button"
            onClick={() => setReloadToken((prev) => prev + 1)}
            className="btn btn-primary"
          >
            Retry
          </button>
        }
      />
    );
  }

  if (!bookings || bookings.length === 0) {
    return (
      <EmptyState
        title="No tickets yet"
        description="Browse events to book your first seats."
        action={
          <Link href="/events" className="btn btn-primary">
            Browse events
          </Link>
        }
      />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {bookings.map((booking) => (
        <TicketCard key={booking.id} booking={booking} event={events[booking.eventId]} />
      ))}
    </div>
  );
}
