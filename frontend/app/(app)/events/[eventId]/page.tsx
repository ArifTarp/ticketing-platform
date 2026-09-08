"use client";

import { use, useEffect, useState } from "react";
import Link from "next/link";
import { fetchEvent } from "@/lib/eventApi";
import { ApiRequestError } from "@/lib/apiClient";
import type { EventResponse } from "@/types/event";
import { EmptyState } from "@/components/common/EmptyState";
import { EventHeader } from "@/components/events/EventHeader";
import { PriceTierList } from "@/components/events/PriceTierList";
import { SelectSeatsButton } from "@/components/events/SelectSeatsButton";

interface EventDetailPageProps {
  params: Promise<{ eventId: string }>;
}

export default function EventDetailPage({ params }: EventDetailPageProps) {
  const { eventId } = use(params);
  const [event, setEvent] = useState<EventResponse | null>(null);
  const [error, setError] = useState<{ status: number; message: string } | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isCancelled = false;
    setIsLoading(true);
    setError(null);
    setEvent(null);

    fetchEvent(eventId)
      .then((result) => {
        if (!isCancelled) {
          setEvent(result);
        }
      })
      .catch((err) => {
        if (isCancelled) {
          return;
        }
        if (err instanceof ApiRequestError) {
          setError({ status: err.status, message: err.detail });
        } else {
          setError({ status: 0, message: "Failed to load event." });
        }
      })
      .finally(() => {
        if (!isCancelled) {
          setIsLoading(false);
        }
      });

    return () => {
      isCancelled = true;
    };
  }, [eventId]);

  if (isLoading) {
    return (
      <div className="flex flex-col gap-6">
        <div className="h-24 animate-pulse rounded-lg bg-zinc-200" />
        <div className="h-40 animate-pulse rounded-lg bg-zinc-200" />
      </div>
    );
  }

  if (error || !event) {
    if (error?.status === 404) {
      return (
        <EmptyState
          title="Event not found"
          description="The event you're looking for doesn't exist or was removed."
          action={
            <Link
              href="/events"
              className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-700"
            >
              Back to events
            </Link>
          }
        />
      );
    }
    return (
      <EmptyState title="Couldn't load this event" description={error?.message ?? "Something went wrong."} />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <EventHeader event={event} />
      <div className="grid grid-cols-1 gap-6 md:grid-cols-[2fr_1fr]">
        <div className="whitespace-pre-line text-sm leading-6 text-zinc-700">{event.description}</div>
        <div className="flex flex-col gap-4 rounded-lg border border-zinc-200 bg-white p-4">
          <PriceTierList seatCategories={event.seatCategories} />
          <SelectSeatsButton event={event} />
        </div>
      </div>
    </div>
  );
}
