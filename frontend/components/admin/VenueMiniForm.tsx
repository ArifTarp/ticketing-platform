"use client";

import { useState, type FormEvent } from "react";
import { createVenue } from "@/lib/adminApi";
import { ApiRequestError } from "@/lib/apiClient";
import type { VenueDto } from "@/types/event";
import { FormError } from "@/components/common/FormError";

interface VenueMiniFormProps {
  onCreated: (venue: VenueDto) => void;
  onCancel: () => void;
}

/**
 * The venue create mini-form (name, address, city) → POST /api/v1/venues. Shared by VenueSelect's
 * inline "+ new venue" option and the standalone "+ Create venue" action (docs/user-flow.md
 * screen 8) so both entry points behave identically.
 */
export function VenueMiniForm({ onCreated, onCancel }: VenueMiniFormProps) {
  const [name, setName] = useState("");
  const [address, setAddress] = useState("");
  const [city, setCity] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    if (!name.trim() || !address.trim() || !city.trim()) {
      setError("Name, address, and city are all required.");
      return;
    }

    setIsSubmitting(true);
    try {
      const venue = await createVenue({ name: name.trim(), address: address.trim(), city: city.trim() });
      onCreated(venue);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.detail : "Failed to create venue.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3">
      <FormError message={error} />
      <label className="flex flex-col gap-1 text-sm">
        Venue name
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          disabled={isSubmitting}
          className="rounded-md border border-zinc-300 px-3 py-2 text-sm focus:border-zinc-500 focus:outline-none disabled:bg-zinc-100"
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        Address
        <input
          type="text"
          value={address}
          onChange={(e) => setAddress(e.target.value)}
          disabled={isSubmitting}
          className="rounded-md border border-zinc-300 px-3 py-2 text-sm focus:border-zinc-500 focus:outline-none disabled:bg-zinc-100"
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        City
        <input
          type="text"
          value={city}
          onChange={(e) => setCity(e.target.value)}
          disabled={isSubmitting}
          className="rounded-md border border-zinc-300 px-3 py-2 text-sm focus:border-zinc-500 focus:outline-none disabled:bg-zinc-100"
        />
      </label>
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={isSubmitting}
          className="rounded-md bg-zinc-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-zinc-700 disabled:cursor-not-allowed disabled:bg-zinc-300"
        >
          {isSubmitting ? "Creating venue…" : "Create venue"}
        </button>
        <button
          type="button"
          onClick={onCancel}
          disabled={isSubmitting}
          className="rounded-md border border-zinc-300 px-3 py-1.5 text-sm text-zinc-700 hover:bg-zinc-100"
        >
          Cancel
        </button>
      </div>
    </form>
  );
}
