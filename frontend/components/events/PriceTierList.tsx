import type { SeatCategoryDto } from "@/types/event";
import { formatPrice } from "@/lib/formatPrice";

interface PriceTierListProps {
  seatCategories: SeatCategoryDto[];
}

/** One row per SeatCategory (name + price) — screen 3's price list. */
export function PriceTierList({ seatCategories }: PriceTierListProps) {
  if (seatCategories.length === 0) {
    return <p className="text-sm text-zinc-500">Pricing not announced yet.</p>;
  }

  return (
    <ul className="flex flex-col gap-2">
      {seatCategories.map((tier) => (
        <li
          key={tier.id}
          className="flex items-center justify-between rounded-md border border-zinc-200 px-3 py-2 text-sm"
        >
          <span className="font-medium text-zinc-700">{tier.name}</span>
          <span className="text-zinc-900">{formatPrice(tier.price)}</span>
        </li>
      ))}
    </ul>
  );
}
