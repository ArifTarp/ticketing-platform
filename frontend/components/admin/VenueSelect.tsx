"use client";

import { useState } from "react";
import type { VenueDto } from "@/types/event";
import { VenueMiniForm } from "./VenueMiniForm";

interface VenueSelectProps {
  venues: VenueDto[];
  selectedVenueId: number | null;
  onSelect: (venueId: number) => void;
  onVenueCreated: (venue: VenueDto) => void;
  isDisabled: boolean;
}

/**
 * Dropdown of known venues plus a "+ new venue" option that expands an inline mini-form
 * (name, address, city) rather than navigating away (docs/user-flow.md screen 8).
 *
 * Note: `services/event` exposes no `GET /api/v1/venues` listing endpoint (only
 * `POST /api/v1/venues`), so "known venues" here means venues created during this admin session —
 * via this mini-form or the standalone "+ Create venue" action — plus, in edit mode, the venue
 * already attached to the event being edited. There is no way to browse the full venue directory
 * from this screen without a backend listing endpoint.
 */
export function VenueSelect({
  venues,
  selectedVenueId,
  onSelect,
  onVenueCreated,
  isDisabled,
}: VenueSelectProps) {
  const [isCreatingVenue, setIsCreatingVenue] = useState(false);

  if (isCreatingVenue) {
    return (
      <div className="rounded-md border border-zinc-200 bg-zinc-50 p-3">
        <VenueMiniForm
          onCreated={(venue) => {
            onVenueCreated(venue);
            onSelect(venue.id);
            setIsCreatingVenue(false);
          }}
          onCancel={() => setIsCreatingVenue(false)}
        />
      </div>
    );
  }

  return (
    <div className="flex gap-2">
      <select
        value={selectedVenueId ?? ""}
        onChange={(e) => onSelect(Number(e.target.value))}
        disabled={isDisabled || venues.length === 0}
        className="flex-1 rounded-md border border-zinc-300 px-3 py-2 text-sm focus:border-zinc-500 focus:outline-none disabled:bg-zinc-100"
      >
        <option value="" disabled>
          {venues.length === 0 ? "No venues yet — create one" : "Select a venue"}
        </option>
        {venues.map((venue) => (
          <option key={venue.id} value={venue.id}>
            {venue.name} ({venue.city})
          </option>
        ))}
      </select>
      <button
        type="button"
        onClick={() => setIsCreatingVenue(true)}
        disabled={isDisabled}
        className="whitespace-nowrap rounded-md border border-zinc-300 px-3 py-2 text-sm text-zinc-700 hover:bg-zinc-100 disabled:cursor-not-allowed disabled:opacity-50"
      >
        + new venue
      </button>
    </div>
  );
}
