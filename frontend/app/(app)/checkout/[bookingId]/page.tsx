"use client";

import { use, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { checkout, getBooking } from "@/lib/bookingApi";
import { ApiRequestError } from "@/lib/apiClient";
import { CHECKOUT_TRIGGERED_STORAGE_KEY_PREFIX } from "@/lib/constants";
import { useAuth } from "@/context/SessionProvider";
import { useBookingPolling } from "@/hooks/useBookingPolling";
import type { BookingResponse } from "@/types/booking";
import { EmptyState } from "@/components/common/EmptyState";
import { FormError } from "@/components/common/FormError";
import { BookingSummary } from "@/components/checkout/BookingSummary";
import { PaymentForm } from "@/components/checkout/PaymentForm";
import { PaymentProcessingOverlay } from "@/components/checkout/PaymentProcessingOverlay";
import { useLocale } from "@/lib/i18n/LocaleContext";

interface CheckoutPageProps {
  params: Promise<{ bookingId: string }>;
}

export default function CheckoutPage({ params }: CheckoutPageProps) {
  const { bookingId } = use(params);
  const router = useRouter();
  const { t } = useLocale();
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth();

  const [booking, setBooking] = useState<BookingResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<{ status: number; message: string } | null>(null);
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [payError, setPayError] = useState<string | null>(null);

  const polling = useBookingPolling(isSubmitted ? bookingId : null);

  function checkoutTriggeredStorageKey() {
    return `${CHECKOUT_TRIGGERED_STORAGE_KEY_PREFIX}${bookingId}`;
  }

  // Fallback trigger for the checkout saga: the normal flow already calls checkout() from the
  // seats screen before navigating here (see handleProceedToPayment in the seats page), marking
  // sessionStorage so this screen knows not to fire it again. A bookmarked/shared/duplicate-tab
  // URL that lands directly here, however, never had that call made — "Pay now" is the fallback
  // trigger in that case (business-rules.md: checkout is the single, sole trigger for the saga).
  async function handlePay() {
    setPayError(null);
    const storageKey = checkoutTriggeredStorageKey();
    const hasAlreadyTriggeredCheckout =
      typeof window !== "undefined" && window.sessionStorage.getItem(storageKey) === "1";

    if (!hasAlreadyTriggeredCheckout && booking?.status === "PENDING") {
      try {
        await checkout(bookingId);
        if (typeof window !== "undefined") {
          window.sessionStorage.setItem(storageKey, "1");
        }
      } catch (err) {
        setPayError(
          err instanceof ApiRequestError ? err.detail : t("checkout.failedToStartCheckout"),
        );
        return;
      }
    }
    setIsSubmitted(true);
  }

  useEffect(() => {
    if (isAuthLoading) {
      return;
    }
    if (!isAuthenticated) {
      router.replace("/login");
    }
  }, [isAuthLoading, isAuthenticated, router]);

  useEffect(() => {
    let isCancelled = false;
    setIsLoading(true);
    setLoadError(null);

    getBooking(bookingId)
      .then((result) => {
        if (isCancelled) return;
        if (result.status !== "PENDING") {
          router.replace(`/checkout/${bookingId}/confirm`);
          return;
        }
        setBooking(result);
      })
      .catch((err) => {
        if (isCancelled) return;
        setLoadError({
          status: err instanceof ApiRequestError ? err.status : 0,
          message: err instanceof ApiRequestError ? err.detail : t("checkout.couldntLoad"),
        });
      })
      .finally(() => {
        if (!isCancelled) setIsLoading(false);
      });

    return () => {
      isCancelled = true;
    };
  }, [bookingId, router]);

  // Resolved: polling saw a terminal status -> navigate to the confirmation screen.
  useEffect(() => {
    if (polling.booking && polling.booking.status !== "PENDING") {
      router.replace(`/checkout/${bookingId}/confirm`);
    }
  }, [polling.booking, bookingId, router]);

  if (isAuthLoading || !isAuthenticated) {
    return null;
  }

  if (isLoading) {
    return (
      <div className="flex flex-col gap-6">
        <div className="h-10 w-64 animate-pulse rounded-[var(--radius-sm)] bg-[var(--surface)]" />
        <div className="h-64 animate-pulse rounded-[var(--radius-md)] bg-[var(--surface)]" />
      </div>
    );
  }

  if (loadError || !booking) {
    return (
      <EmptyState
        title={loadError?.status === 404 ? t("checkout.bookingNotFound") : t("checkout.couldntLoad")}
        description={loadError?.message ?? t("common.somethingWentWrong")}
      />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-3">
        <span className="value-mono flex h-5 w-5 shrink-0 items-center justify-center rounded-full border border-[var(--accent-border)] bg-[var(--accent-soft)] text-xs text-[var(--accent)]">
          1
        </span>
        <h1 className="font-[family-name:var(--font-display)] text-xl font-semibold text-[var(--text-primary)]">
          {t("checkout.title")}
        </h1>
      </div>
      <div className="grid grid-cols-1 gap-6 md:grid-cols-[1fr_1fr]">
        {isSubmitted ? (
          <PaymentProcessingOverlay />
        ) : (
          <PaymentForm isDisabled={isSubmitted} onPay={handlePay} />
        )}
        <BookingSummary booking={polling.booking ?? booking} />
      </div>
      {payError && <FormError message={payError} />}
      {polling.hasTimedOut && <FormError message={t("checkout.takingLonger")} />}
      {polling.error && <FormError message={polling.error} />}
    </div>
  );
}
