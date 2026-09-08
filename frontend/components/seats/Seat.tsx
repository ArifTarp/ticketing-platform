import type { SeatDto } from "@/types/event";
import type { SeatVisualStatus } from "@/lib/seatMap";

interface SeatProps {
  seat: SeatDto;
  status: SeatVisualStatus;
  onClick: (seat: SeatDto) => void;
}

const STATUS_CLASSES: Record<SeatVisualStatus, string> = {
  AVAILABLE: "border-zinc-300 bg-white text-zinc-700 hover:border-zinc-900 cursor-pointer",
  SELECTED: "border-zinc-900 bg-zinc-900 text-white cursor-pointer",
  HELD: "border-zinc-200 bg-zinc-200 text-zinc-400 cursor-not-allowed",
  SOLD: "border-zinc-800 bg-zinc-800 text-zinc-400 cursor-not-allowed",
};

/** Single seat square; visual variant is a pure function of its merged SeatAvailability status. */
export function Seat({ seat, status, onClick }: SeatProps) {
  const isClickable = status === "AVAILABLE" || status === "SELECTED";

  return (
    <button
      type="button"
      disabled={!isClickable}
      onClick={() => onClick(seat)}
      title={`${seat.section} ${seat.row}${seat.number} — ${seat.seatCategory.name}`}
      aria-pressed={status === "SELECTED"}
      className={`flex h-8 w-8 items-center justify-center rounded-md border text-[10px] font-medium transition-colors ${STATUS_CLASSES[status]}`}
    >
      {seat.number}
    </button>
  );
}
