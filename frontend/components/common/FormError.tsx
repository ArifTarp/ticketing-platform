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
      className="rounded-md border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      {message}
    </div>
  );
}
