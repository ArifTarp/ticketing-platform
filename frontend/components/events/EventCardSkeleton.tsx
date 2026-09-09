/** Loading placeholder for EventCard, shown while GET /api/v1/events is in flight. */
export function EventCardSkeleton() {
  return (
    <div className="panel flex flex-col gap-3 p-4">
      <div className="h-20 w-full animate-pulse rounded-[var(--radius-sm)] bg-[var(--surface-hover)]" />
      <div className="h-4 w-2/3 animate-pulse rounded bg-[var(--surface-hover)]" />
      <div className="h-3 w-1/2 animate-pulse rounded bg-[var(--surface-hover)]" />
      <div className="h-3 w-1/3 animate-pulse rounded bg-[var(--surface-hover)]" />
      <div className="h-4 w-1/4 animate-pulse rounded bg-[var(--surface-hover)]" />
    </div>
  );
}
