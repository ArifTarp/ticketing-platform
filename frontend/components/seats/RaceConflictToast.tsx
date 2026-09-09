interface RaceConflictToastProps {
  message: string | null;
  onDismiss: () => void;
}

/** Transient toast for a 409 seat-race response (screen 4) — someone else grabbed the seat first. */
export function RaceConflictToast({ message, onDismiss }: RaceConflictToastProps) {
  if (!message) {
    return null;
  }

  return (
    <div
      role="alert"
      className="flex items-start justify-between gap-3 rounded-[var(--radius-sm)] border px-4 py-3 text-sm"
      style={{
        borderColor: "var(--status-danger)",
        backgroundColor: "var(--status-danger-soft)",
        color: "var(--status-danger)",
      }}
    >
      <span className="flex items-start gap-2">
        <span className="status-dot mt-1.5" style={{ backgroundColor: "var(--status-danger)" }} aria-hidden />
        {message}
      </span>
      <button
        type="button"
        onClick={onDismiss}
        aria-label="Dismiss"
        className="text-[var(--status-danger)] opacity-80 hover:opacity-100"
      >
        ×
      </button>
    </div>
  );
}
