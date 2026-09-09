"use client";

import { useEffect, useState, type FormEvent } from "react";
import { createEvent, createSeatCategories } from "@/lib/adminApi";
import { fetchEvent } from "@/lib/eventApi";
import { ApiRequestError } from "@/lib/apiClient";
import type { CreateSeatCategoryRequest, EventStatus, SeatCategoryDto, VenueDto } from "@/types/event";
import { FormError } from "@/components/common/FormError";
import { VenueSelect } from "./VenueSelect";
import {
  SeatCategoryList,
  createEmptySeatCategoryRow,
  isSeatCategoryRowValid,
  type SeatCategoryRow,
} from "./SeatCategoryList";

const STATUS_OPTIONS: EventStatus[] = ["DRAFT", "ON_SALE", "SOLD_OUT", "CLOSED"];

interface EventFormProps {
  mode: "create" | "edit";
  /** Required when mode === "edit". */
  eventId: number | null;
  venues: VenueDto[];
  onVenueCreated: (venue: VenueDto) => void;
  onClose: () => void;
  onSuccess: () => void;
}

/**
 * Create/edit form (docs/user-flow.md screen 8): title, venue picker, description, starts-at,
 * status, and an embedded SeatCategoryList.
 *
 * Known limitation (edit mode): `services/event` exposes no `PUT`/`PATCH /api/v1/events/{id}` —
 * only `POST /api/v1/events` (create) and `POST /api/v1/events/{id}/seat-categories` (add new
 * tiers) exist. So in edit mode the event's own fields (title/description/starts-at/status) are
 * shown read-only and cannot be saved; the form still lets an admin add *additional* seat
 * categories to an existing event, since that part of the API genuinely supports it.
 */
export function EventForm({ mode, eventId, venues, onVenueCreated, onClose, onSuccess }: EventFormProps) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [startsAt, setStartsAt] = useState("");
  const [status, setStatus] = useState<EventStatus>("DRAFT");
  const [selectedVenueId, setSelectedVenueId] = useState<number | null>(null);
  const [existingCategories, setExistingCategories] = useState<SeatCategoryDto[]>([]);
  const [rows, setRows] = useState<SeatCategoryRow[]>(
    mode === "create" ? [createEmptySeatCategoryRow()] : [],
  );
  const [createdEventId, setCreatedEventId] = useState<number | null>(null);
  const [isLoadingDetail, setIsLoadingDetail] = useState(mode === "edit");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (mode !== "edit" || eventId === null) {
      return;
    }
    let cancelled = false;
    setIsLoadingDetail(true);
    fetchEvent(eventId)
      .then((event) => {
        if (cancelled) return;
        setTitle(event.title);
        setDescription(event.description ?? "");
        setStartsAt(toDatetimeLocalValue(event.startsAt));
        setStatus(event.status);
        setSelectedVenueId(event.venue.id);
        setExistingCategories(event.seatCategories);
        onVenueCreated(event.venue);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err instanceof ApiRequestError ? err.detail : "Failed to load this event.");
      })
      .finally(() => {
        if (!cancelled) setIsLoadingDetail(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, eventId]);

  const validRows = rows.filter(isSeatCategoryRowValid);
  const isCreateModeValid =
    mode === "create" &&
    title.trim() !== "" &&
    selectedVenueId !== null &&
    startsAt !== "" &&
    validRows.length > 0 &&
    validRows.length === rows.length;
  const isEditModeValid = mode === "edit" && rows.length > 0 && validRows.length === rows.length;
  const isSaveDisabled = isSubmitting || isLoadingDetail || (!isCreateModeValid && !isEditModeValid);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      const requests: CreateSeatCategoryRequest[] = validRows.map((row) => ({
        name: row.name.trim(),
        section: row.section.trim(),
        price: Number(row.price),
      }));

      if (mode === "create") {
        let targetEventId = createdEventId;
        if (targetEventId === null) {
          if (selectedVenueId === null) {
            throw new Error("A venue is required.");
          }
          const created = await createEvent({
            venueId: selectedVenueId,
            title: title.trim(),
            description: description.trim(),
            startsAt: new Date(startsAt).toISOString(),
            status,
          });
          targetEventId = created.id;
          setCreatedEventId(created.id);
        }
        if (requests.length > 0) {
          await createSeatCategories(targetEventId, requests);
        }
      } else if (eventId !== null && requests.length > 0) {
        await createSeatCategories(eventId, requests);
      }

      onSuccess();
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 403) {
        setError("You don't have permission to do this.");
      } else if (err instanceof ApiRequestError) {
        setError(err.detail);
      } else {
        setError("Something went wrong. Please try again.");
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  const isFieldsDisabled = isSubmitting || isLoadingDetail || mode === "edit";

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/60 px-4">
      <div className="panel max-h-[90vh] w-full max-w-lg overflow-y-auto p-6">
        <h2 className="mb-4 font-[family-name:var(--font-display)] text-lg font-semibold text-[var(--text-primary)]">
          {mode === "create" ? "Create event" : "Edit event"}
        </h2>

        {isLoadingDetail ? (
          <div className="flex flex-col gap-3">
            <div className="h-4 w-2/3 animate-pulse rounded-[var(--radius-sm)] bg-[var(--surface-hover)]" />
            <div className="h-24 animate-pulse rounded-[var(--radius-sm)] bg-[var(--surface-hover)]" />
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {mode === "edit" && (
              <p
                className="rounded-[var(--radius-sm)] border px-3 py-2 text-sm"
                style={{
                  borderColor: "var(--status-pending)",
                  backgroundColor: "var(--status-pending-soft)",
                  color: "var(--status-pending)",
                }}
              >
                Editing an existing event&apos;s details isn&apos;t supported yet — the event
                service has no update endpoint. You can still add new seat categories below.
              </p>
            )}

            <label className="flex flex-col gap-1 text-sm text-[var(--text-secondary)]">
              <span className="label-mono">Title</span>
              <input
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                disabled={isFieldsDisabled}
                className="input-field disabled:opacity-50"
              />
            </label>

            <label className="flex flex-col gap-1 text-sm text-[var(--text-secondary)]">
              <span className="label-mono">Venue</span>
              <VenueSelect
                venues={venues}
                selectedVenueId={selectedVenueId}
                onSelect={setSelectedVenueId}
                onVenueCreated={onVenueCreated}
                isDisabled={isFieldsDisabled}
              />
            </label>

            <label className="flex flex-col gap-1 text-sm text-[var(--text-secondary)]">
              <span className="label-mono">Description</span>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                disabled={isFieldsDisabled}
                rows={3}
                className="input-field disabled:opacity-50"
              />
            </label>

            <label className="flex flex-col gap-1 text-sm text-[var(--text-secondary)]">
              <span className="label-mono">Starts at</span>
              <input
                type="datetime-local"
                value={startsAt}
                onChange={(e) => setStartsAt(e.target.value)}
                disabled={isFieldsDisabled}
                className="input-field value-mono disabled:opacity-50"
              />
            </label>

            <label className="flex flex-col gap-1 text-sm text-[var(--text-secondary)]">
              <span className="label-mono">Status</span>
              <select
                value={status}
                onChange={(e) => setStatus(e.target.value as EventStatus)}
                disabled={isFieldsDisabled}
                className="input-field disabled:opacity-50"
              >
                {STATUS_OPTIONS.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </label>

            <SeatCategoryList
              existingCategories={existingCategories}
              rows={rows}
              onRowsChange={setRows}
              isDisabled={isSubmitting}
            />

            <FormError message={error} />

            <div className="flex justify-end gap-2">
              <button type="button" onClick={onClose} disabled={isSubmitting} className="btn btn-secondary">
                Cancel
              </button>
              <button type="submit" disabled={isSaveDisabled} className="btn btn-primary">
                {isSubmitting ? "Saving…" : "Save event"}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}

/** Converts an ISO-8601 instant into the local `datetime-local` input value format. */
function toDatetimeLocalValue(isoDate: string): string {
  const date = new Date(isoDate);
  const offsetMs = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16);
}
