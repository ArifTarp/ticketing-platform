/** Formats an ISO-8601 instant (e.g. Event.startsAt) as a medium date + short time string. */
export function formatEventDate(isoDate: string): string {
  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(isoDate));
}
