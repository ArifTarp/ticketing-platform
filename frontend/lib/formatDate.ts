import type { Locale } from "./i18n/LocaleContext";

/**
 * Formats an ISO-8601 instant (e.g. Event.startsAt) as a medium date + short time string, always
 * in the Europe/Istanbul timezone (the events are Istanbul-local, root CLAUDE.md's Turkish-market
 * demo) regardless of the viewer's own timezone. `locale` threads through the current i18n locale
 * (see lib/i18n/LocaleContext.tsx) so the date itself reads in TR or EN conventions; defaults to
 * "tr" for callers that don't have a locale in scope (e.g. admin screens, tests).
 */
export function formatEventDate(isoDate: string, locale: Locale = "tr"): string {
  const intlLocale = locale === "tr" ? "tr-TR" : "en-US";
  return new Intl.DateTimeFormat(intlLocale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "Europe/Istanbul",
  }).format(new Date(isoDate));
}
