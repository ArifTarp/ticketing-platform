const LEGEND_ITEMS: { label: string; className: string }[] = [
  { label: "Available", className: "border-zinc-300 bg-white" },
  { label: "Selected", className: "border-zinc-900 bg-zinc-900" },
  { label: "Held", className: "border-zinc-200 bg-zinc-200" },
  { label: "Sold", className: "border-zinc-800 bg-zinc-800" },
];

/** Static legend for the four seat visual states — screen 4's SeatMapLegend. */
export function SeatMapLegend() {
  return (
    <ul className="flex flex-wrap gap-4 text-xs text-zinc-600">
      {LEGEND_ITEMS.map((item) => (
        <li key={item.label} className="flex items-center gap-2">
          <span className={`h-4 w-4 rounded border ${item.className}`} />
          {item.label}
        </li>
      ))}
    </ul>
  );
}
