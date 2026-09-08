import type { ReactNode } from "react";

interface AuthLayoutProps {
  children: ReactNode;
}

/** Centered card shell shared by /login and /register (screen 1). */
export function AuthLayout({ children }: AuthLayoutProps) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-zinc-50 px-4 py-12">
      <div className="w-full max-w-md">
        <p className="mb-6 text-center text-xl font-semibold text-zinc-900">Ticketing</p>
        <div className="rounded-xl border border-zinc-200 bg-white p-6 shadow-sm">{children}</div>
      </div>
    </div>
  );
}
