import type { ReactNode } from "react";

interface AuthLayoutProps {
  children: ReactNode;
}

/** Centered card shell shared by /login and /register (screen 1). */
export function AuthLayout({ children }: AuthLayoutProps) {
  return (
    <div className="bg-grid flex min-h-screen items-center justify-center bg-[var(--bg)] px-4 py-12">
      <div className="w-full max-w-md">
        <p className="mb-6 flex items-center justify-center gap-2 font-[family-name:var(--font-display)] text-xl font-semibold tracking-tight text-[var(--text-primary)]">
          <span className="inline-block h-2 w-2 rounded-full bg-[var(--accent)] shadow-[0_0_10px_var(--accent)]" />
          Ticketing
        </p>
        <div className="panel p-6">{children}</div>
      </div>
    </div>
  );
}
