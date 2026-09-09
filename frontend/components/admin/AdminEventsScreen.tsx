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
        <div className="flex flex-col gap-1">
          <span className="label-mono">Control panel</span>
          <h1 className="font-[family-name:var(--font-display)] text-2xl font-semibold text-[var(--text-primary)]">
            Admin
          </h1>
        </div>
        <div className="flex gap-2">
          <button type="button" onClick={() => setFormState({ mode: "create" })} className="btn btn-primary">
            + Create event
          </button>
          <button type="button" onClick={() => setIsVenueModalOpen(true)} className="btn btn-secondary">
            + Create venue
          </button>
        </div>
      </div>

      <AdminTabs activeTab={activeTab} onTabChange={setActiveTab} />

      {activeTab === "events" && <EventTable reloadToken={reloadToken} onEdit={handleEdit} />}

      {activeTab === "venues" && (
        <div className="panel p-4">
          {venues.length === 0 ? (
            <p className="text-sm text-[var(--text-secondary)]">
              No venues created this session yet. Use &quot;+ Create venue&quot; above, or the
              inline &quot;+ new venue&quot; option in the event form.
            </p>
          ) : (
            <ul className="flex flex-col divide-y divide-[var(--border)] text-sm">
              {venues.map((venue) => (
                <li key={venue.id} className="flex justify-between py-2.5">
                  <span className="font-medium text-[var(--text-primary)]">{venue.name}</span>
                  <span className="value-mono text-[var(--text-muted)]">
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
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/60 px-4">
          <div className="panel w-full max-w-sm p-6">
            <h2 className="mb-4 font-[family-name:var(--font-display)] text-lg font-semibold text-[var(--text-primary)]">
              Create venue
            </h2>
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
