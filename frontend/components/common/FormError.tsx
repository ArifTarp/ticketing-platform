interface FormErrorProps {
  message: string | null;
}

/** Generic inline error banner, used for 401 on login, 409 on register, and similar form-level errors. */
export function FormError({ message }: FormErrorProps) {
  if (!message) {
    return null;
  }

  return (
    <div
      role="alert"
      className="flex items-start gap-2 rounded-[var(--radius-sm)] border px-4 py-3 text-sm"
      style={{
        borderColor: "var(--status-danger)",
        backgroundColor: "var(--status-danger-soft)",
        color: "var(--status-danger)",
      }}
    >
      <span className="status-dot mt-1.5 bg-[var(--status-danger)]" aria-hidden />
      <span>{message}</span>
    </div>
  );
}
