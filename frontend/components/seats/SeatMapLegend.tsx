import { useLocale } from "@/lib/i18n/LocaleContext";

const LEGEND_ITEMS: { key: string; color: string }[] = [
  { key: "seats.legend.available", color: "var(--border-strong)" },
  { key: "seats.legend.selected", color: "var(--accent)" },
  { key: "seats.legend.held", color: "var(--status-pending)" },
  { key: "seats.legend.sold", color: "var(--status-danger)" },
];

/** Static legend for the four seat visual states — screen 4's SeatMapLegend. */
export function SeatMapLegend() {
  const { t } = useLocale();

  return (
    <ul className="flex flex-wrap gap-4">
      {LEGEND_ITEMS.map((item) => (
        <li key={item.key} className="label-mono flex items-center gap-2">
          <span className="status-dot" style={{ backgroundColor: item.color }} aria-hidden />
          {t(item.key)}
        </li>
      ))}
    </ul>
  );
}
