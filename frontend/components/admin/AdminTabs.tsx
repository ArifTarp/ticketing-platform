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
    <div className="flex gap-1 border-b border-[var(--border)]" role="tablist">
      {TABS.map((tab) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          aria-selected={activeTab === tab.id}
          onClick={() => onTabChange(tab.id)}
          className={`label-mono -mb-px border-b-2 px-4 py-2.5 transition-colors ${
            activeTab === tab.id
              ? "border-[var(--accent)] text-[var(--text-primary)]"
              : "border-transparent text-[var(--text-muted)] hover:text-[var(--text-secondary)]"
          }`}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}
