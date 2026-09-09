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
      <div className="rounded-[var(--radius-sm)] border border-[var(--border)] bg-[var(--surface-sunken)] p-3">
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
        className="input-field flex-1 disabled:opacity-50"
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
        className="btn btn-secondary whitespace-nowrap disabled:cursor-not-allowed"
      >
        + new venue
      </button>
    </div>
  );
}
