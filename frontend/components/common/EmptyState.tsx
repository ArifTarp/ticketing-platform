import type { ReactNode } from "react";

interface EmptyStateProps {
  title: string;
  description?: string;
  action?: ReactNode;
}

/** Shared "nothing to show" component — no results, fetch failure + retry, 404, etc. */
export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="bg-grid flex flex-col items-center justify-center gap-3 rounded-[var(--radius-lg)] border border-dashed border-[var(--border-strong)] px-6 py-16 text-center">
      <p className="font-[family-name:var(--font-display)] text-base font-medium text-[var(--text-primary)]">
        {title}
      </p>
      {description && (
        <p className="max-w-sm text-sm text-[var(--text-secondary)]">{description}</p>
      )}
      {action}
    </div>
  );
}
