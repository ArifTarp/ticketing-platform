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
import { BookingSummary } from "@/components/checkout/BookingSummary";
import { PaymentForm } from "@/components/checkout/PaymentForm";
import { PaymentProcessingOverlay } from "@/components/checkout/PaymentProcessingOverlay";

interface CheckoutPageProps {
  params: Promise<{ bookingId: string }>;
}

export default function CheckoutPage({ params }: CheckoutPageProps) {
  const { bookingId } = use(params);
  const router = useRouter();
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
          err instanceof ApiRequestError ? err.detail : "Failed to start checkout.",
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
          message: err instanceof ApiRequestError ? err.detail : "Failed to load this booking.",
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
        <div className="h-10 w-64 animate-pulse rounded-md bg-zinc-200" />
        <div className="h-64 animate-pulse rounded-lg bg-zinc-200" />
      </div>
    );
  }

  if (loadError || !booking) {
    return (
      <EmptyState
        title={loadError?.status === 404 ? "Booking not found" : "Couldn't load this booking"}
        description={loadError?.message ?? "Something went wrong."}
      />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-xl font-semibold text-zinc-900">Checkout</h1>
      <div className="grid grid-cols-1 gap-6 md:grid-cols-[1fr_1fr]">
        {isSubmitted ? (
          <PaymentProcessingOverlay />
        ) : (
          <PaymentForm isDisabled={isSubmitted} onPay={handlePay} />
        )}
        <BookingSummary booking={polling.booking ?? booking} />
      </div>
      {payError && (
        <p className="text-sm text-red-700" role="alert">
          {payError}
        </p>
      )}
      {polling.hasTimedOut && (
        <p className="text-sm text-red-700" role="alert">
          This is taking longer than expected. Refresh the page to check the latest status.
        </p>
      )}
      {polling.error && (
        <p className="text-sm text-red-700" role="alert">
          {polling.error}
        </p>
      )}
    </div>
  );
}
