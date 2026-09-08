import type { Metadata } from "next";
import { EventList } from "@/components/events/EventList";

export const metadata: Metadata = { title: "Events — Ticketing" };

export default function EventsPage() {
  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-semibold text-zinc-900">Upcoming events</h1>
      <EventList />
    </div>
  );
}
