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
    <div className="panel flex flex-wrap items-end gap-3 p-4">
      <div className="flex flex-col gap-1">
        <label htmlFor="filter-city" className="label-mono">
          City
        </label>
        <input
          id="filter-city"
          value={city}
          onChange={(event) => setCity(event.target.value)}
          placeholder="Any city"
          className="input-field"
        />
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor="filter-from" className="label-mono">
          From
        </label>
        <input
          id="filter-from"
          type="date"
          value={from}
          onChange={(event) => setFrom(event.target.value)}
          className="input-field"
        />
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor="filter-to" className="label-mono">
          To
        </label>
        <input
          id="filter-to"
          type="date"
          value={to}
          onChange={(event) => setTo(event.target.value)}
          className="input-field"
        />
      </div>
      <div className="flex flex-1 flex-col gap-1">
        <label htmlFor="filter-search" className="label-mono">
          Search
        </label>
        <input
          id="filter-search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search events…"
          className="input-field w-full"
        />
      </div>
    </div>
  );
}
