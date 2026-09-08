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
        className="w-full rounded-md border border-zinc-300 px-3 py-2 pr-16 text-sm focus:border-zinc-500 focus:outline-none disabled:bg-zinc-100"
      />
      <button
        type="button"
        onClick={() => setIsVisible((prev) => !prev)}
        className="absolute right-2 top-1/2 -translate-y-1/2 text-xs font-medium text-zinc-500 hover:text-zinc-700"
      >
        {isVisible ? "Hide" : "Show"}
      </button>
    </div>
  );
}
