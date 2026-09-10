"use client";

import Link from "next/link";
import { useAuth } from "@/context/SessionProvider";
import { useLocale } from "@/lib/i18n/LocaleContext";

/** Visible TR/EN toggle — flips `LocaleProvider`'s locale, persisted to localStorage. */
function LocaleToggle() {
  const { locale, setLocale } = useLocale();

  return (
    <div className="flex items-center gap-1 rounded-full border border-[var(--border)] p-0.5 text-xs">
      {(["tr", "en"] as const).map((option) => (
        <button
          key={option}
          type="button"
          onClick={() => setLocale(option)}
          aria-pressed={locale === option}
          className={`label-mono rounded-full px-2 py-1 transition-colors ${
            locale === option
              ? "bg-[var(--accent-soft)] text-[var(--accent-strong)]"
              : "text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
          }`}
        >
          {option.toUpperCase()}
        </button>
      ))}
    </div>
  );
}

export function NavBar() {
  const { isAuthenticated, user, logout } = useAuth();
  const { t } = useLocale();
  const isAdmin = Boolean(user?.roles.includes("ADMIN"));

  return (
    <header className="sticky top-0 z-40 border-b border-[var(--border)] bg-[var(--bg)]/90 backdrop-blur-sm">
      <nav className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <Link
          href="/events"
          className="flex items-center gap-2 font-[family-name:var(--font-display)] text-lg font-semibold tracking-tight text-[var(--text-primary)]"
        >
          <span className="inline-block h-2 w-2 rounded-full bg-[var(--accent)] shadow-[0_0_10px_var(--accent)]" />
          {t("nav.brand")}
        </Link>
        <div className="flex items-center gap-5 text-sm">
          <Link
            href="/events"
            className="text-[var(--text-secondary)] transition hover:text-[var(--text-primary)]"
          >
            {t("nav.events")}
          </Link>
          {isAuthenticated ? (
            <>
              <Link
                href="/tickets"
                className="text-[var(--text-secondary)] transition hover:text-[var(--text-primary)]"
              >
                {t("nav.myTickets")}
              </Link>
              {isAdmin && (
                <Link
                  href="/admin/events"
                  className="text-[var(--text-secondary)] transition hover:text-[var(--text-primary)]"
                >
                  {t("nav.admin")}
                </Link>
              )}
              <span className="label-mono hidden sm:inline">{user?.email}</span>
              <LocaleToggle />
              <button type="button" onClick={logout} className="btn btn-secondary">
                {t("nav.logOut")}
              </button>
            </>
          ) : (
            <>
              <LocaleToggle />
              <Link href="/login" className="btn btn-primary">
                {t("nav.logIn")}
              </Link>
            </>
          )}
        </div>
      </nav>
    </header>
  );
}
