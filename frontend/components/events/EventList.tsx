"use client";

import { useCallback, useEffect, useState } from "react";
import { fetchEvents } from "@/lib/eventApi";
import { ApiRequestError } from "@/lib/apiClient";
import type { EventFilterParams, EventSummaryResponse } from "@/types/event";
import { EmptyState } from "@/components/common/EmptyState";
import { EventCard } from "./EventCard";
import { EventCardSkeleton } from "./EventCardSkeleton";
import { EventFilterBar } from "./EventFilterBar";

const SKELETON_COUNT = 6;

/** Grid/list container for screen 2 — owns the fetch and its loading/empty/error/loaded states. */
export function EventList() {
  const [filters, setFilters] = useState<EventFilterParams>({});
  const [events, setEvents] = useState<EventSummaryResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [reloadToken, setReloadToken] = useState(0);

  const loadEvents = useCallback(async (currentFilters: EventFilterParams) => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchEvents(currentFilters);
      setEvents(result);
    } catch (err) {
      setEvents(null);
      setError(err instanceof ApiRequestError ? err.detail : "Failed to load events.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadEvents(filters);
  }, [filters, reloadToken, loadEvents]);

  return (
    <div className="flex flex-col gap-6">
      <EventFilterBar onFilterChange={setFilters} />

      {isLoading && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: SKELETON_COUNT }).map((_, index) => (
            <EventCardSkeleton key={index} />
          ))}
        </div>
      )}

      {!isLoading && error && (
        <EmptyState
          title="Couldn't load events"
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
      )}

      {!isLoading && !error && events && events.length === 0 && (
        <EmptyState title="No events found" description="Try different filters." />
      )}

      {!isLoading && !error && events && events.length > 0 && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {events.map((event) => (
            <EventCard key={event.id} event={event} />
          ))}
        </div>
      )}
    </div>
  );
}
