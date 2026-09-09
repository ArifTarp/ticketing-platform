"use client";

import Link from "next/link";
import { useAuth } from "@/context/SessionProvider";

export function NavBar() {
  const { isAuthenticated, user, logout } = useAuth();
  const isAdmin = Boolean(user?.roles.includes("ADMIN"));

  return (
    <header className="border-b border-zinc-200 bg-white">
      <nav className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <Link href="/events" className="text-lg font-semibold text-zinc-900">
          Ticketing
        </Link>
        <div className="flex items-center gap-4 text-sm">
          <Link href="/events" className="text-zinc-600 hover:text-zinc-900">
            Events
          </Link>
          {isAuthenticated ? (
            <>
              <Link href="/tickets" className="text-zinc-600 hover:text-zinc-900">
                My tickets
              </Link>
              {isAdmin && (
                <Link href="/admin/events" className="text-zinc-600 hover:text-zinc-900">
                  Admin
                </Link>
              )}
              <span className="text-zinc-500">{user?.email}</span>
              <button
                type="button"
                onClick={logout}
                className="rounded-md border border-zinc-300 px-3 py-1.5 text-zinc-700 hover:bg-zinc-100"
              >
                Log out
              </button>
            </>
          ) : (
            <Link
              href="/login"
              className="rounded-md bg-zinc-900 px-3 py-1.5 text-white hover:bg-zinc-700"
            >
              Log in
            </Link>
          )}
        </div>
      </nav>
    </header>
  );
}
