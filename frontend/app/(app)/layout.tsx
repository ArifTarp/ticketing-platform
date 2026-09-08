import type { ReactNode } from "react";
import { NavBar } from "@/components/common/NavBar";

export default function AppRouteLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-zinc-50">
      <NavBar />
      <main className="mx-auto w-full max-w-6xl flex-1 px-6 py-8">{children}</main>
    </div>
  );
}
