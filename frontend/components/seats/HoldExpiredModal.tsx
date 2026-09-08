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
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
    >
      <div className="flex w-full max-w-sm flex-col gap-4 rounded-lg bg-white p-6 shadow-lg">
        <h2 className="text-base font-semibold text-zinc-900">Your hold expired</h2>
        <p className="text-sm text-zinc-600">
          You didn&apos;t complete checkout within the 10-minute hold window, so your selected
          seats were released. Pick your seats again to continue.
        </p>
        <button
          type="button"
          onClick={onDismiss}
          className="w-full rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-700"
        >
          Select seats again
        </button>
      </div>
    </div>
  );
}
