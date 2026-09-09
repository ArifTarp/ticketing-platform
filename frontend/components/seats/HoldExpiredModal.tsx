interface HoldExpiredModalProps {
  isOpen: boolean;
  onDismiss: () => void;
}

/** Blocking modal shown when the screen-4 countdown hits 00:00 before checkout. */
export function HoldExpiredModal({ isOpen, onDismiss }: HoldExpiredModalProps) {
  if (!isOpen) {
    return null;
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4"
    >
      <div className="panel flex w-full max-w-sm flex-col gap-4 p-6">
        <div className="flex items-center gap-2">
          <span
            className="status-dot"
            style={{ backgroundColor: "var(--status-danger)" }}
            aria-hidden
          />
          <h2 className="font-[family-name:var(--font-display)] text-base font-semibold text-[var(--text-primary)]">
            Your hold expired
          </h2>
        </div>
        <p className="text-sm text-[var(--text-secondary)]">
          You didn&apos;t complete checkout within the 10-minute hold window, so your selected
          seats were released. Pick your seats again to continue.
        </p>
        <button type="button" onClick={onDismiss} className="btn btn-primary w-full">
          Select seats again
        </button>
      </div>
    </div>
  );
}
