import type { Metadata } from "next";
import { AdminGuard } from "@/components/admin/AdminGuard";
import { AdminEventsScreen } from "@/components/admin/AdminEventsScreen";

export const metadata: Metadata = { title: "Admin: events — Ticketing" };

export default function AdminEventsPage() {
  return (
    <AdminGuard>
      <AdminEventsScreen />
    </AdminGuard>
  );
}
