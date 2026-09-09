"use client";

import { useEffect, type ReactNode } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/SessionProvider";
import { EmptyState } from "@/components/common/EmptyState";
import { resolveAdminGuardStatus } from "@/lib/adminGuard";

interface AdminGuardProps {
  children: ReactNode;
}

/**
 * Route-level wrapper for /admin/events (docs/user-flow.md screen 8). Redirects a logged-out user
 * to /login, renders a 403 EmptyState for an authenticated non-ADMIN user, and only ever mounts
 * `children` (EventTable/EventForm) once the session resolves to an ADMIN JWT — mirrors the
 * gateway/backend rejecting non-ADMIN callers on the write routes. No admin API call is attempted
 * from anywhere in the child tree until this guard passes.
 */
export function AdminGuard({ children }: AdminGuardProps) {
  const router = useRouter();
  const { isAuthenticated, isLoading, user } = useAuth();
  const status = resolveAdminGuardStatus({
    isLoading,
    isAuthenticated,
    roles: user?.roles,
  });

  useEffect(() => {
    if (status === "unauthenticated") {
      router.replace("/login");
    }
  }, [status, router]);

  if (status === "loading" || status === "unauthenticated") {
    return null;
  }

  if (status === "forbidden") {
    return (
      <EmptyState
        title="You don't have access to this page"
        description="Admin permissions are required to manage events and venues."
        action={
          <Link
            href="/events"
            className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-700"
          >
            Back to events
          </Link>
        }
      />
    );
  }

  return <>{children}</>;
}
