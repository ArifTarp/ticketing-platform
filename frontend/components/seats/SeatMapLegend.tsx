const LEGEND_ITEMS: { label: string; color: string }[] = [
  { label: "Available", color: "var(--border-strong)" },
  { label: "Selected", color: "var(--accent)" },
  { label: "Held", color: "var(--status-pending)" },
  { label: "Sold", color: "var(--status-danger)" },
];

/** Static legend for the four seat visual states — screen 4's SeatMapLegend. */
export function SeatMapLegend() {
  return (
    <ul className="flex flex-wrap gap-4">
      {LEGEND_ITEMS.map((item) => (
        <li key={item.label} className="label-mono flex items-center gap-2">
          <span className="status-dot" style={{ backgroundColor: item.color }} aria-hidden />
          {item.label}
        </li>
      ))}
    </ul>
  );
}
