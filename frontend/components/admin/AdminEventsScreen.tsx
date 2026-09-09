"use client";

import { useState } from "react";
import type { EventSummaryResponse, VenueDto } from "@/types/event";
import { AdminTabs, type AdminTab } from "./AdminTabs";
import { EventTable } from "./EventTable";
import { EventForm } from "./EventForm";
import { VenueMiniForm } from "./VenueMiniForm";

/**
 * Screen-level state for /admin/events (docs/user-flow.md screen 8): tab switching, the create/edit
 * modal, the session-scoped known-venues list, and refetching the table after a successful save.
 * Only ever mounted inside AdminGuard once the ADMIN check has passed.
 */
export function AdminEventsScreen() {
  const [activeTab, setActiveTab] = useState<AdminTab>("events");
  const [venues, setVenues] = useState<VenueDto[]>([]);
  const [formState, setFormState] = useState<
    { mode: "create" } | { mode: "edit"; eventId: number } | null
  >(null);
  const [isVenueModalOpen, setIsVenueModalOpen] = useState(false);
  const [reloadToken, setReloadToken] = useState(0);

  function addKnownVenue(venue: VenueDto) {
    setVenues((prev) => (prev.some((v) => v.id === venue.id) ? prev : [...prev, venue]));
  }

  function handleEdit(event: EventSummaryResponse) {
    setFormState({ mode: "edit", eventId: event.id });
  }

  function handleFormSuccess() {
    setFormState(null);
    setReloadToken((prev) => prev + 1);
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-zinc-900">Admin</h1>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => setFormState({ mode: "create" })}
            className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-700"
          >
            + Create event
          </button>
          <button
            type="button"
            onClick={() => setIsVenueModalOpen(true)}
            className="rounded-md border border-zinc-300 px-4 py-2 text-sm font-medium text-zinc-700 hover:bg-zinc-100"
          >
            + Create venue
          </button>
        </div>
      </div>

      <AdminTabs activeTab={activeTab} onTabChange={setActiveTab} />

      {activeTab === "events" && <EventTable reloadToken={reloadToken} onEdit={handleEdit} />}

      {activeTab === "venues" && (
        <div className="rounded-lg border border-zinc-200 bg-white p-4">
          {venues.length === 0 ? (
            <p className="text-sm text-zinc-500">
              No venues created this session yet. Use &quot;+ Create venue&quot; above, or the
              inline &quot;+ new venue&quot; option in the event form.
            </p>
          ) : (
            <ul className="flex flex-col divide-y divide-zinc-100 text-sm">
              {venues.map((venue) => (
                <li key={venue.id} className="flex justify-between py-2">
                  <span className="font-medium text-zinc-900">{venue.name}</span>
                  <span className="text-zinc-500">
                    {venue.address}, {venue.city}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {formState && (
        <EventForm
          mode={formState.mode}
          eventId={formState.mode === "edit" ? formState.eventId : null}
          venues={venues}
          onVenueCreated={addKnownVenue}
          onClose={() => setFormState(null)}
          onSuccess={handleFormSuccess}
        />
      )}

      {isVenueModalOpen && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 px-4">
          <div className="w-full max-w-sm rounded-lg bg-white p-6 shadow-xl">
            <h2 className="mb-4 text-lg font-semibold text-zinc-900">Create venue</h2>
            <VenueMiniForm
              onCreated={(venue) => {
                addKnownVenue(venue);
                setIsVenueModalOpen(false);
              }}
              onCancel={() => setIsVenueModalOpen(false)}
            />
          </div>
        </div>
      )}
    </div>
  );
}
