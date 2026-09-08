"use client";

import { useEffect, useState } from "react";
import type { EventFilterParams } from "@/types/event";

interface EventFilterBarProps {
  onFilterChange: (filters: EventFilterParams) => void;
}

const DEBOUNCE_MS = 400;

/** City / date-range / free-text search, debounced before triggering a re-fetch. */
export function EventFilterBar({ onFilterChange }: EventFilterBarProps) {
  const [city, setCity] = useState("");
  const [query, setQuery] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      onFilterChange({
        city: city.trim() || undefined,
        q: query.trim() || undefined,
        from: from ? new Date(from).toISOString() : undefined,
        to: to ? new Date(to).toISOString() : undefined,
      });
    }, DEBOUNCE_MS);

    return () => window.clearTimeout(timeoutId);
  }, [city, query, from, to, onFilterChange]);

  return (
    <div className="flex flex-wrap items-end gap-3 rounded-lg border border-zinc-200 bg-white p-4">
      <div className="flex flex-col gap-1">
        <label htmlFor="filter-city" className="text-xs font-medium text-zinc-500">
          City
        </label>
        <input
          id="filter-city"
          value={city}
          onChange={(event) => setCity(event.target.value)}
          placeholder="Any city"
          className="rounded-md border border-zinc-300 px-3 py-1.5 text-sm focus:border-zinc-500 focus:outline-none"
        />
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor="filter-from" className="text-xs font-medium text-zinc-500">
          From
        </label>
        <input
          id="filter-from"
          type="date"
          value={from}
          onChange={(event) => setFrom(event.target.value)}
          className="rounded-md border border-zinc-300 px-3 py-1.5 text-sm focus:border-zinc-500 focus:outline-none"
        />
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor="filter-to" className="text-xs font-medium text-zinc-500">
          To
        </label>
        <input
          id="filter-to"
          type="date"
          value={to}
          onChange={(event) => setTo(event.target.value)}
          className="rounded-md border border-zinc-300 px-3 py-1.5 text-sm focus:border-zinc-500 focus:outline-none"
        />
      </div>
      <div className="flex flex-1 flex-col gap-1">
        <label htmlFor="filter-search" className="text-xs font-medium text-zinc-500">
          Search
        </label>
        <input
          id="filter-search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search events…"
          className="w-full rounded-md border border-zinc-300 px-3 py-1.5 text-sm focus:border-zinc-500 focus:outline-none"
        />
      </div>
    </div>
  );
}
