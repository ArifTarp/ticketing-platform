"use client";

import { use, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { fetchEventSeats } from "@/lib/eventApi";
import { fetchSeatAvailability, checkout } from "@/lib/bookingApi";
import { ApiRequestError } from "@/lib/apiClient";
import { useAuth } from "@/context/SessionProvider";
import { useSeatSelection } from "@/hooks/useSeatSelection";
import { useCountdown } from "@/hooks/useCountdown";
import { toAvailabilityMap } from "@/lib/seatMap";
import type { SeatMapResponse } from "@/types/event";
import type { SeatAvailabilityResponse } from "@/types/booking";
import { EmptyState } from "@/components/common/EmptyState";
import { CountdownTimer } from "@/components/common/CountdownTimer";
import { SeatMap } from "@/components/seats/SeatMap";
import { SeatMapLegend } from "@/components/seats/SeatMapLegend";
import { SelectionSummary } from "@/components/seats/SelectionSummary";
import { RaceConflictToast } from "@/components/seats/RaceConflictToast";
import { HoldExpiredModal } from "@/components/seats/HoldExpiredModal";

interface SeatSelectionPageProps {
  params: Promise<{ eventId: string }>;
}

export default function SeatSelectionPage({ params }: SeatSelectionPageProps) {
  const { eventId } = use(params);
  const router = useRouter();
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
      })
      .catch((err) => {
        if (isCancelled) return;
        setLoadError(
          err instanceof ApiRequestError ? err.detail : "Failed to load the seat map.",
        );
      })
      .finally(() => {
        if (!isCancelled) setIsLoading(false);
      });

    return () => {
      isCancelled = true;
    };
  }, [eventId]);

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
        setRaceConflictMessage(`${err.detail} Please choose a different seat.`);
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
      router.push(`/checkout/${selection.booking.id}`);
    } catch (err) {
      setCheckoutError(
        err instanceof ApiRequestError ? err.detail : "Failed to start checkout.",
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
        <div className="h-10 w-64 animate-pulse rounded-md bg-zinc-200" />
        <div className="h-96 animate-pulse rounded-lg bg-zinc-200" />
      </div>
    );
  }

  if (loadError || !seatMap) {
    return (
      <EmptyState
        title="Couldn't load the seat map"
        description={loadError ?? "Something went wrong."}
        action={
          <Link
            href={`/events/${eventId}`}
            className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-700"
          >
            Back to event
          </Link>
        }
      />
    );
  }

  const availabilityBySeatId = toAvailabilityMap(availability);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-zinc-900">Select your seats</h1>
        <CountdownTimer expiresAt={selection.booking?.expiresAt ?? null} />
      </div>

      <RaceConflictToast
        message={raceConflictMessage}
        onDismiss={() => setRaceConflictMessage(null)}
      />
      {checkoutError && (
        <p className="text-sm text-red-700" role="alert">
          {checkoutError}
        </p>
      )}

      <div className="grid grid-cols-1 gap-6 md:grid-cols-[2fr_1fr]">
        <div className="flex flex-col gap-4 rounded-lg border border-zinc-200 bg-white p-4">
          <SeatMap
            seats={seatMap.seats}
            availabilityBySeatId={availabilityBySeatId}
            selectedSeatIds={selection.selectedSeatIds}
            onToggleSeat={selection.toggleSeat}
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
