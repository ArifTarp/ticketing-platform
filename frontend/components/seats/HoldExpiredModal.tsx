import { useLocale } from "@/lib/i18n/LocaleContext";

interface HoldExpiredModalProps {
  isOpen: boolean;
  onDismiss: () => void;
}

/** Blocking modal shown when the screen-4 countdown hits 00:00 before checkout. */
export function HoldExpiredModal({ isOpen, onDismiss }: HoldExpiredModalProps) {
  const { t } = useLocale();

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
            {t("seats.holdExpired.title")}
          </h2>
        </div>
        <p className="text-sm text-[var(--text-secondary)]">{t("seats.holdExpired.description")}</p>
        <button type="button" onClick={onDismiss} className="btn btn-primary w-full">
          {t("seats.holdExpired.cta")}
        </button>
      </div>
    </div>
  );
}
