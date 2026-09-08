/** Shown while `useBookingPolling` waits for the async checkout saga's result (screen 5). */
export function PaymentProcessingOverlay() {
  return (
    <div className="flex flex-col items-center justify-center gap-4 rounded-lg border border-zinc-200 bg-white px-6 py-16 text-center">
      <div
        aria-hidden="true"
        className="h-8 w-8 animate-spin rounded-full border-2 border-zinc-300 border-t-zinc-900"
      />
      <p className="text-sm font-medium text-zinc-700">Confirming your payment…</p>
      <p className="text-xs text-zinc-500">This can take a few seconds.</p>
    </div>
  );
}
