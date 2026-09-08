/** Loading placeholder for EventCard, shown while GET /api/v1/events is in flight. */
export function EventCardSkeleton() {
  return (
    <div className="flex flex-col gap-3 rounded-lg border border-zinc-200 bg-white p-4">
      <div className="h-4 w-2/3 animate-pulse rounded bg-zinc-200" />
      <div className="h-3 w-1/2 animate-pulse rounded bg-zinc-200" />
      <div className="h-3 w-1/3 animate-pulse rounded bg-zinc-200" />
      <div className="h-4 w-1/4 animate-pulse rounded bg-zinc-200" />
    </div>
  );
}
