"use client";

import { use, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getBooking } from "@/lib/bookingApi";
import { ApiRequestError } from "@/lib/apiClient";
import { useAuth } from "@/context/SessionProvider";
import type { BookingResponse } from "@/types/booking";
import { EmptyState } from "@/components/common/EmptyState";
import { BookingSuccessPanel } from "@/components/checkout/BookingSuccessPanel";
import { BookingFailurePanel } from "@/components/checkout/BookingFailurePanel";

interface ConfirmPageProps {
  params: Promise<{ bookingId: string }>;
}

export default function ConfirmPage({ params }: ConfirmPageProps) {
  const { bookingId } = use(params);
  const router = useRouter();
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth();

  const [booking, setBooking] = useState<BookingResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

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
        if (!isCancelled) setBooking(result);
      })
      .catch((err) => {
        if (isCancelled) return;
        setLoadError(err instanceof ApiRequestError ? err.detail : "Failed to load this booking.");
      })
      .finally(() => {
        if (!isCancelled) setIsLoading(false);
      });

    return () => {
      isCancelled = true;
    };
  }, [bookingId]);

  if (isAuthLoading || !isAuthenticated) {
    return null;
  }

  if (isLoading) {
    return <div className="h-64 animate-pulse rounded-[var(--radius-md)] bg-[var(--surface)]" />;
  }

  if (loadError || !booking) {
    return <EmptyState title="Couldn't load this booking" description={loadError ?? "Something went wrong."} />;
  }

  if (booking.status === "PENDING") {
    // Per docs/user-flow.md screen 6: only arrived at via screen 5's polling resolution, so a
    // still-PENDING booking here means the user navigated directly — send them back to checkout.
    router.replace(`/checkout/${bookingId}`);
    return null;
  }

  return (
    <div className="mx-auto w-full max-w-md">
      {booking.status === "CONFIRMED" ? (
        <BookingSuccessPanel booking={booking} />
      ) : (
        <BookingFailurePanel booking={booking} />
      )}
    </div>
  );
}
