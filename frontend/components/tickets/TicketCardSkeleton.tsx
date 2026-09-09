/** Loading placeholder for TicketCard, shown while bookings/event details are in flight. */
export function TicketCardSkeleton() {
  return (
    <div className="panel flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex flex-1 flex-col gap-2">
        <div className="h-4 w-2/3 animate-pulse rounded-[var(--radius-sm)] bg-[var(--surface-hover)]" />
        <div className="h-3 w-1/2 animate-pulse rounded-[var(--radius-sm)] bg-[var(--surface-hover)]" />
        <div className="h-3 w-1/3 animate-pulse rounded-[var(--radius-sm)] bg-[var(--surface-hover)]" />
      </div>
      <div className="h-32 w-32 animate-pulse rounded-[var(--radius-md)] border border-[var(--border)] bg-[var(--surface-hover)]" />
    </div>
  );
}
