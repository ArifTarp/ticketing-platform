"use client";

import { EventList } from "@/components/events/EventList";
import { useLocale } from "@/lib/i18n/LocaleContext";

export default function EventsPage() {
  const { t } = useLocale();
  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-[family-name:var(--font-display)] text-2xl font-semibold text-[var(--text-primary)]">
        {t("events.pageTitle")}
      </h1>
      <EventList />
    </div>
  );
}
