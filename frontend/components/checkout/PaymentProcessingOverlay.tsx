/** Shown while `useBookingPolling` waits for the async checkout saga's result (screen 5). */
export function PaymentProcessingOverlay() {
  return (
    <div className="bg-grid panel flex flex-col items-center justify-center gap-4 px-6 py-16 text-center">
      <div
        aria-hidden="true"
        className="h-8 w-8 animate-spin rounded-full border-2 border-[var(--border-strong)] border-t-[var(--accent)]"
      />
      <p className="label-mono text-[var(--text-primary)]">Confirming your payment…</p>
      <p className="text-xs text-[var(--text-muted)]">This can take a few seconds.</p>
    </div>
  );
}
