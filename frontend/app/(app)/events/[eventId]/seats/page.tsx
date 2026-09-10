"use client";

import { use, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { fetchEventSeats } from "@/lib/eventApi";
import { fetchSeatAvailability, fetchPendingBooking, checkout } from "@/lib/bookingApi";
import { ApiRequestError } from "@/lib/apiClient";
import { useAuth } from "@/context/SessionProvider";
import { useSeatSelection } from "@/hooks/useSeatSelection";
import { useCountdown } from "@/hooks/useCountdown";
import { toAvailabilityMap } from "@/lib/seatMap";
import { CHECKOUT_TRIGGERED_STORAGE_KEY_PREFIX } from "@/lib/constants";
import type { SeatMapResponse } from "@/types/event";
import type { SeatAvailabilityResponse } from "@/types/booking";
import { EmptyState } from "@/components/common/EmptyState";
import { CountdownTimer } from "@/components/common/CountdownTimer";
import { SeatMap } from "@/components/seats/SeatMap";
import { SeatMapLegend } from "@/components/seats/SeatMapLegend";
import { SelectionSummary } from "@/components/seats/SelectionSummary";
import { RaceConflictToast } from "@/components/seats/RaceConflictToast";
import { HoldExpiredModal } from "@/components/seats/HoldExpiredModal";
import { useLocale } from "@/lib/i18n/LocaleContext";

interface SeatSelectionPageProps {
  params: Promise<{ eventId: string }>;
}

export default function SeatSelectionPage({ params }: SeatSelectionPageProps) {
  const { eventId } = use(params);
  const router = useRouter();
  const { t } = useLocale();
  const { isAuthenticated, isLoading: isAuthLoading, user } = useAuth();

  const [seatMap, setSeatMap] = useState<SeatMapResponse | null>(null);
  const [availability, setAvailability] = useState<SeatAvailabilityResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isCheckingOut, setIsCheckingOut] = useState(false);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const [isHoldExpiredModalOpen, setIsHoldExpiredModalOpen] = useState(false);
  const [raceConflictMessage, setRaceConflictMessage] = useState<string | null>(null);

  const userId = user?.userId ?? "";
  const selection = useSeatSelection({ eventId, userId });
  const { isExpired } = useCountdown(selection.booking?.expiresAt ?? null);

  const loadAvailability = useCallback(() => {
    return fetchSeatAvailability(eventId).then(setAvailability);
  }, [eventId]);

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

    Promise.all([fetchEventSeats(eventId), fetchSeatAvailability(eventId)])
      .then(([layout, availabilityRows]) => {
        if (isCancelled) return;
        setSeatMap(layout);
        setAvailability(availabilityRows);

        // Bug fix: resume an already-PENDING hold (e.g. this same user refreshed mid-hold)
        // instead of starting from an empty selection — otherwise the seat map shows their own
        // seats as HELD (red, unclickable) with no way back to the countdown/checkout UI they
        // already had. Best-effort: a failure here just leaves the page in its normal empty-
        // selection state, same as before this fix.
        if (userId) {
          fetchPendingBooking(userId, eventId)
            .then((pendingBooking) => {
              if (!isCancelled && pendingBooking) {
                selection.resumeBooking(pendingBooking, layout.seats);
              }
            })
            .catch(() => undefined);
        }
      })
      .catch((err) => {
        if (isCancelled) return;
        setLoadError(err instanceof ApiRequestError ? err.detail : t("seats.couldntLoad"));
      })
      .finally(() => {
        if (!isCancelled) setIsLoading(false);
      });

    return () => {
      isCancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventId, userId, selection.resumeBooking]);

  // Screen-4 rule: countdown hits 00:00 before checkout -> blocking modal, refetch availability,
  // clear the (now-expired) selection/booking.
  useEffect(() => {
    if (isExpired && selection.booking) {
      setIsHoldExpiredModalOpen(true);
      selection.clearSelection();
      loadAvailability().catch(() => undefined);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isExpired]);

  async function handleHoldSelectedSeats() {
    setRaceConflictMessage(null);
    try {
      await selection.holdSelectedSeats();
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 409) {
        setRaceConflictMessage(`${err.detail} ${t("seats.raceConflictSuffix")}`);
        await loadAvailability().catch(() => undefined);
      }
    }
  }

  async function handleProceedToPayment() {
    if (!selection.booking) {
      return;
    }
    setIsCheckingOut(true);
    setCheckoutError(null);
    try {
      await checkout(selection.booking.id);
      if (typeof window !== "undefined") {
        window.sessionStorage.setItem(
          `${CHECKOUT_TRIGGERED_STORAGE_KEY_PREFIX}${selection.booking.id}`,
          "1",
        );
      }
      router.push(`/checkout/${selection.booking.id}`);
    } catch (err) {
      setCheckoutError(
        err instanceof ApiRequestError ? err.detail : t("seats.failedToStartCheckout"),
      );
    } finally {
      setIsCheckingOut(false);
    }
  }

  if (isAuthLoading || !isAuthenticated) {
    return null;
  }

  if (isLoading) {
    return (
      <div className="flex flex-col gap-6">
        <div className="h-10 w-64 animate-pulse rounded-[var(--radius-sm)] bg-[var(--surface-hover)]" />
        <div className="h-96 animate-pulse rounded-[var(--radius-md)] bg-[var(--surface-hover)]" />
      </div>
    );
  }

  if (loadError || !seatMap) {
    return (
      <EmptyState
        title={t("seats.couldntLoad")}
        description={loadError ?? t("common.somethingWentWrong")}
        action={
          <Link href={`/events/${eventId}`} className="btn btn-primary">
            {t("seats.backToEvent")}
          </Link>
        }
      />
    );
  }

  const availabilityBySeatId = toAvailabilityMap(availability);

  return (
    <div className="flex flex-col gap-6 pb-4">
      <div className="flex items-center justify-between">
        <h1 className="font-[family-name:var(--font-display)] text-xl font-semibold text-[var(--text-primary)]">
          {t("seats.pageTitle")}
        </h1>
        <CountdownTimer expiresAt={selection.booking?.expiresAt ?? null} />
      </div>

      <RaceConflictToast
        message={raceConflictMessage}
        onDismiss={() => setRaceConflictMessage(null)}
      />
      {checkoutError && (
        <p
          className="rounded-[var(--radius-sm)] border px-4 py-3 text-sm"
          role="alert"
          style={{
            borderColor: "var(--status-danger)",
            backgroundColor: "var(--status-danger-soft)",
            color: "var(--status-danger)",
          }}
        >
          {checkoutError}
        </p>
      )}

      <div className="grid grid-cols-1 gap-6 md:grid-cols-[2fr_1fr]">
        <div className="panel flex flex-col gap-4 p-4">
          <SeatMap
            seats={seatMap.seats}
            availabilityBySeatId={availabilityBySeatId}
            selectedSeatIds={selection.selectedSeatIds}
            onToggleSeat={selection.toggleSeat}
            isLocked={Boolean(selection.booking)}
          />
          <SeatMapLegend />
        </div>
        <SelectionSummary
          selectedSeats={selection.selectedSeats}
          total={selection.total}
          isHolding={selection.isHolding}
          holdError={selection.holdError}
          maxSeatsReached={selection.maxSeatsReached}
          isHeld={Boolean(selection.booking)}
          isCheckingOut={isCheckingOut}
          onHoldSelectedSeats={handleHoldSelectedSeats}
          onProceedToPayment={handleProceedToPayment}
        />
      </div>

      <HoldExpiredModal
        isOpen={isHoldExpiredModalOpen}
        onDismiss={() => setIsHoldExpiredModalOpen(false)}
      />
    </div>
  );
}
