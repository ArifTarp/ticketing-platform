"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/context/SessionProvider";
import { registerUser } from "@/lib/authApi";
import { ApiRequestError } from "@/lib/apiClient";
import { FormError } from "@/components/common/FormError";
import { PasswordInput } from "./PasswordInput";

export function RegisterForm() {
  const router = useRouter();
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const passwordsMatch = password === confirmPassword;
  const isSubmitDisabled =
    !email.trim() || !password || !confirmPassword || !passwordsMatch || isSubmitting;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    if (!passwordsMatch) {
      setError("Passwords do not match.");
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await registerUser({ email, password });
      login(response.token);
      router.push("/events");
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 409) {
        setError("An account with this email already exists.");
      } else if (err instanceof ApiRequestError) {
        setError(err.detail);
      } else {
        setError("Something went wrong. Please try again.");
      }
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <h1 className="text-lg font-semibold text-zinc-900">Create an account</h1>

      <div className="flex flex-col gap-1">
        <label htmlFor="register-email" className="text-sm font-medium text-zinc-700">
          Email
        </label>
        <input
          id="register-email"
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          disabled={isSubmitting}
          autoComplete="email"
          className="w-full rounded-md border border-zinc-300 px-3 py-2 text-sm focus:border-zinc-500 focus:outline-none disabled:bg-zinc-100"
        />
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="register-password" className="text-sm font-medium text-zinc-700">
          Password
        </label>
        <PasswordInput
          id="register-password"
          value={password}
          onChange={setPassword}
          autoComplete="new-password"
          isDisabled={isSubmitting}
        />
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="register-confirm-password" className="text-sm font-medium text-zinc-700">
          Confirm password
        </label>
        <PasswordInput
          id="register-confirm-password"
          value={confirmPassword}
          onChange={setConfirmPassword}
          autoComplete="new-password"
          isDisabled={isSubmitting}
        />
      </div>

      <FormError message={error} />

      <button
        type="submit"
        disabled={isSubmitDisabled}
        className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-700 disabled:cursor-not-allowed disabled:bg-zinc-300"
      >
        {isSubmitting ? "Creating account…" : "Create account"}
      </button>

      <p className="text-center text-sm text-zinc-500">
        Already have an account?{" "}
        <Link href="/login" className="font-medium text-zinc-900 hover:underline">
          Log in instead
        </Link>
      </p>
    </form>
  );
}
