"use client";

import Link from "next/link";
import { useAuth } from "@/context/SessionProvider";

export function NavBar() {
  const { isAuthenticated, user, logout } = useAuth();
  const isAdmin = Boolean(user?.roles.includes("ADMIN"));

  return (
    <header className="sticky top-0 z-40 border-b border-[var(--border)] bg-[var(--bg)]/90 backdrop-blur-sm">
      <nav className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <Link
          href="/events"
          className="flex items-center gap-2 font-[family-name:var(--font-display)] text-lg font-semibold tracking-tight text-[var(--text-primary)]"
        >
          <span className="inline-block h-2 w-2 rounded-full bg-[var(--accent)] shadow-[0_0_10px_var(--accent)]" />
          Ticketing
        </Link>
        <div className="flex items-center gap-5 text-sm">
          <Link
            href="/events"
            className="text-[var(--text-secondary)] transition hover:text-[var(--text-primary)]"
          >
            Events
          </Link>
          {isAuthenticated ? (
            <>
              <Link
                href="/tickets"
                className="text-[var(--text-secondary)] transition hover:text-[var(--text-primary)]"
              >
                My tickets
              </Link>
              {isAdmin && (
                <Link
                  href="/admin/events"
                  className="text-[var(--text-secondary)] transition hover:text-[var(--text-primary)]"
                >
                  Admin
                </Link>
              )}
              <span className="label-mono hidden sm:inline">{user?.email}</span>
              <button type="button" onClick={logout} className="btn btn-secondary">
                Log out
              </button>
            </>
          ) : (
            <Link href="/login" className="btn btn-primary">
              Log in
            </Link>
          )}
        </div>
      </nav>
    </header>
  );
}
