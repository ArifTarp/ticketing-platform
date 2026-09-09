export type AdminTab = "events" | "venues";

interface AdminTabsProps {
  activeTab: AdminTab;
  onTabChange: (tab: AdminTab) => void;
}

const TABS: { id: AdminTab; label: string }[] = [
  { id: "events", label: "Events" },
  { id: "venues", label: "Venues" },
];

/**
 * Switches between the Events list and a Venues list (docs/user-flow.md screen 8). Venues are
 * created inline from the event form via VenueSelect's "+ new venue" option (or the standalone
 * "+ Create venue" action), so a full separate venue CRUD screen is out of scope for the demo —
 * the Venues tab is a read-only view of venues created this session.
 */
export function AdminTabs({ activeTab, onTabChange }: AdminTabsProps) {
  return (
    <div className="flex gap-1 border-b border-zinc-200" role="tablist">
      {TABS.map((tab) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          aria-selected={activeTab === tab.id}
          onClick={() => onTabChange(tab.id)}
          className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium ${
            activeTab === tab.id
              ? "border-zinc-900 text-zinc-900"
              : "border-transparent text-zinc-500 hover:text-zinc-700"
          }`}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}
