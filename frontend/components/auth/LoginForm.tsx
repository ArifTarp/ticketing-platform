"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/context/SessionProvider";
import { loginUser } from "@/lib/authApi";
import { ApiRequestError } from "@/lib/apiClient";
import { useLocale } from "@/lib/i18n/LocaleContext";
import { FormError } from "@/components/common/FormError";
import { PasswordInput } from "./PasswordInput";

export function LoginForm() {
  const router = useRouter();
  const { login } = useAuth();
  const { t } = useLocale();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const isSubmitDisabled = !email.trim() || !password || isSubmitting;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      const response = await loginUser({ email, password });
      login(response.token);
      router.push("/events");
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 401) {
        setError(t("auth.login.invalidCredentials"));
      } else if (err instanceof ApiRequestError) {
        setError(err.detail);
      } else {
        setError(t("common.somethingWentWrong"));
      }
      setPassword("");
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <div>
        <h1 className="font-[family-name:var(--font-display)] text-lg font-semibold text-[var(--text-primary)]">
          {t("auth.login.title")}
        </h1>
        <p className="label-mono mt-1">{t("auth.login.subtitle")}</p>
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="login-email" className="label-mono">
          {t("auth.login.email")}
        </label>
        <input
          id="login-email"
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          disabled={isSubmitting}
          autoComplete="email"
          className="input-field w-full disabled:opacity-50"
        />
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="login-password" className="label-mono">
          {t("auth.login.password")}
        </label>
        <PasswordInput
          id="login-password"
          value={password}
          onChange={setPassword}
          autoComplete="current-password"
          isDisabled={isSubmitting}
        />
      </div>

      <FormError message={error} />

      <button type="submit" disabled={isSubmitDisabled} className="btn btn-primary w-full">
        {isSubmitting ? t("auth.login.submitting") : t("auth.login.submit")}
      </button>

      <p className="text-center text-sm text-[var(--text-muted)]">
        {t("auth.login.noAccount")}{" "}
        <Link href="/register" className="font-medium text-[var(--accent)] hover:text-[var(--accent-strong)]">
          {t("auth.login.registerInstead")}
        </Link>
      </p>
    </form>
  );
}
