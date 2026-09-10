"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/context/SessionProvider";
import { registerUser } from "@/lib/authApi";
import { ApiRequestError } from "@/lib/apiClient";
import { useLocale } from "@/lib/i18n/LocaleContext";
import { FormError } from "@/components/common/FormError";
import { PasswordInput } from "./PasswordInput";

export function RegisterForm() {
  const router = useRouter();
  const { login } = useAuth();
  const { t } = useLocale();
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
      setError(t("auth.register.passwordsMismatch"));
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await registerUser({ email, password });
      login(response.token);
      router.push("/events");
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 409) {
        setError(t("auth.register.emailTaken"));
      } else if (err instanceof ApiRequestError) {
        setError(err.detail);
      } else {
        setError(t("common.somethingWentWrong"));
      }
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <div>
        <h1 className="font-[family-name:var(--font-display)] text-lg font-semibold text-[var(--text-primary)]">
          {t("auth.register.title")}
        </h1>
        <p className="label-mono mt-1">{t("auth.register.subtitle")}</p>
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="register-email" className="label-mono">
          {t("auth.register.email")}
        </label>
        <input
          id="register-email"
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          disabled={isSubmitting}
          autoComplete="email"
          className="input-field w-full disabled:opacity-50"
        />
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="register-password" className="label-mono">
          {t("auth.register.password")}
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
        <label htmlFor="register-confirm-password" className="label-mono">
          {t("auth.register.confirmPassword")}
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

      <button type="submit" disabled={isSubmitDisabled} className="btn btn-primary w-full">
        {isSubmitting ? t("auth.register.submitting") : t("auth.register.submit")}
      </button>

      <p className="text-center text-sm text-[var(--text-muted)]">
        {t("auth.register.haveAccount")}{" "}
        <Link href="/login" className="font-medium text-[var(--accent)] hover:text-[var(--accent-strong)]">
          {t("auth.register.loginInstead")}
        </Link>
      </p>
    </form>
  );
}
