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
      className="flex items-start justify-between gap-3 rounded-md border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-700 shadow-sm"
    >
      <span>{message}</span>
      <button
        type="button"
        onClick={onDismiss}
        aria-label="Dismiss"
        className="text-red-500 hover:text-red-700"
      >
        ×
      </button>
    </div>
  );
}
