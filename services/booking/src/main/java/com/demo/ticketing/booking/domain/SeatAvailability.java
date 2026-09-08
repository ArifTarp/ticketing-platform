package com.demo.ticketing.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Per-{@code (eventId, seatId)} sale state — the durable record backing the Redis distributed
 * lock's outcome (ADR-0001, business-rules.md). A seat with no row here is implicitly
 * {@link SeatAvailabilityStatus#AVAILABLE}.
 *
 * <p>{@link #markHeld()} is used by the Phase 7 hold endpoint ({@code BookingHoldService}). The
 * SOLD/AVAILABLE transitions (payment completion, release on failure/expiry) are Phase 8, built by
 * {@code saga-orchestrator} on top of this schema.
 */
@Entity
@Table(name = "seat_availability")
public class SeatAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SeatAvailabilityStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SeatAvailability() {
        // JPA
    }

    public SeatAvailability(Long eventId, Long seatId, SeatAvailabilityStatus status) {
        this.eventId = eventId;
        this.seatId = seatId;
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getSeatId() {
        return seatId;
    }

    public SeatAvailabilityStatus getStatus() {
        return status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Transitions this row to {@link SeatAvailabilityStatus#HELD}. Callers (only
     * {@code BookingHoldService}) must have already confirmed the current status is
     * {@link SeatAvailabilityStatus#AVAILABLE} (or that no row existed yet) — this method does not
     * re-check, since the Redis distributed lock is what serializes concurrent callers before this
     * is ever invoked (see {@code SeatHoldLockService}).
     */
    public void markHeld() {
        this.status = SeatAvailabilityStatus.HELD;
        this.updatedAt = Instant.now();
    }
}
