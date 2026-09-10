"use client";

import { useCallback, useEffect, useState } from "react";
import { deleteEvent, fetchAdminEvents } from "@/lib/adminApi";
import { ApiRequestError } from "@/lib/apiClient";
import { formatEventDate } from "@/lib/formatDate";
import type { EventSummaryResponse } from "@/types/event";
import { EmptyState } from "@/components/common/EmptyState";
import { EventStatusBadge } from "@/components/events/EventStatusBadge";

const SKELETON_ROW_COUNT = 4;

interface EventTableProps {
  /** Bumping this value re-triggers the fetch — used to refetch after a successful create. */
  reloadToken: number;
  onEdit: (event: EventSummaryResponse) => void;
}

/**
 * List container for the admin events table (docs/user-flow.md screen 8) — owns fetch +
 * loading/empty/error/loaded state, same pattern as EventList (screen 2), but sources from
 * GET /api/v1/admin/events (ADMIN-only, every status) instead of the public fetchEvents().
 */
export function EventTable({ reloadToken, onEdit }: EventTableProps) {
  const [events, setEvents] = useState<EventSummaryResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [retryToken, setRetryToken] = useState(0);
  const [deletingEventId, setDeletingEventId] = useState<number | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const loadEvents = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchAdminEvents();
      setEvents(result);
    } catch (err) {
      setEvents(null);
      setError(err instanceof ApiRequestError ? err.detail : "Failed to load events.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadEvents();
  }, [loadEvents, reloadToken, retryToken]);

  async function handleDelete(event: EventSummaryResponse) {
    if (!window.confirm(`Delete "${event.title}"? This can't be undone.`)) {
      return;
    }
    setDeleteError(null);
    setDeletingEventId(event.id);
    try {
      await deleteEvent(event.id);
      setEvents((prev) => (prev ? prev.filter((e) => e.id !== event.id) : prev));
    } catch (err) {
      setDeleteError(err instanceof ApiRequestError ? err.detail : "Failed to delete this event.");
    } finally {
      setDeletingEventId(null);
    }
  }

  if (isLoading) {
    return (
      <div className="panel flex flex-col gap-2 p-3">
        {Array.from({ length: SKELETON_ROW_COUNT }).map((_, index) => (
          <div key={index} className="h-10 animate-pulse rounded-[var(--radius-sm)] bg-[var(--surface-hover)]" />
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <EmptyState
        title="Couldn't load events"
        description={error}
        action={
          <button type="button" onClick={() => setRetryToken((prev) => prev + 1)} className="btn btn-primary">
            Retry
          </button>
        }
      />
    );
  }

  if (!events || events.length === 0) {
    return <EmptyState title="No events yet" description="Create one to get started." />;
  }

  return (
    <div className="flex flex-col gap-2">
      {deleteError && (
        <p
          className="rounded-[var(--radius-sm)] border px-3 py-2 text-sm"
          style={{
            borderColor: "var(--status-danger)",
            backgroundColor: "var(--status-danger-soft)",
            color: "var(--status-danger)",
          }}
        >
          {deleteError}
        </p>
      )}
      <div className="panel overflow-hidden">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-[var(--border)]">
            <tr>
              <th className="label-mono px-4 py-3 font-medium">Title</th>
              <th className="label-mono px-4 py-3 font-medium">Venue</th>
              <th className="label-mono px-4 py-3 font-medium">Starts at</th>
              <th className="label-mono px-4 py-3 font-medium">Status</th>
              <th className="label-mono px-4 py-3 font-medium">
                <span className="sr-only">Actions</span>
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--border)]">
            {events.map((event) => (
              <tr key={event.id} className="transition-colors hover:bg-[var(--surface-hover)]">
                <td className="px-4 py-3 font-medium text-[var(--text-primary)]">{event.title}</td>
                <td className="px-4 py-3 text-[var(--text-secondary)]">{event.venueName}</td>
                <td className="value-mono px-4 py-3 text-[var(--text-secondary)]">
                  {formatEventDate(event.startsAt)}
                </td>
                <td className="px-4 py-3">
                  <EventStatusBadge status={event.status} />
                </td>
                <td className="px-4 py-3 text-right">
                  <div className="flex justify-end gap-3">
                    <button
                      type="button"
                      onClick={() => onEdit(event)}
                      className="label-mono text-[var(--text-secondary)] hover:text-[var(--accent)]"
                    >
                      Edit
                    </button>
                    <button
                      type="button"
                      onClick={() => handleDelete(event)}
                      disabled={deletingEventId === event.id}
                      className="label-mono text-[var(--text-secondary)] hover:text-[var(--status-danger)] disabled:opacity-50"
                    >
                      {deletingEventId === event.id ? "Deleting…" : "Delete"}
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
