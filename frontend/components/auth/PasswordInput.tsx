"use client";

import { useState } from "react";

interface PasswordInputProps {
  id: string;
  value: string;
  onChange: (value: string) => void;
  autoComplete?: string;
  isDisabled?: boolean;
}

/** Password field with a show/hide toggle, shared by LoginForm and RegisterForm. */
export function PasswordInput({ id, value, onChange, autoComplete, isDisabled }: PasswordInputProps) {
  const [isVisible, setIsVisible] = useState(false);

  return (
    <div className="relative">
      <input
        id={id}
        type={isVisible ? "text" : "password"}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        autoComplete={autoComplete}
        disabled={isDisabled}
        className="input-field w-full pr-16 disabled:opacity-50"
      />
      <button
        type="button"
        onClick={() => setIsVisible((prev) => !prev)}
        className="label-mono absolute right-2 top-1/2 -translate-y-1/2 tracking-normal normal-case text-[var(--text-secondary)] transition hover:text-[var(--text-primary)]"
      >
        {isVisible ? "Hide" : "Show"}
      </button>
    </div>
  );
}
