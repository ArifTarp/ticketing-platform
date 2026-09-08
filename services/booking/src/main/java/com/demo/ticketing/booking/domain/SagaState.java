package com.demo.ticketing.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Tracks one {@link Booking}'s progress through the checkout saga (business-rules.md), so a crash
 * mid-saga can be recovered/replayed instead of leaving a booking stuck.
 *
 * <p>{@code step}/{@code status} are deliberately plain strings, not enums — the {@code
 * saga-orchestrator} agent (Phase 8) owns the actual vocabulary (e.g.
 * {@code step=PAYMENT_REQUESTED, status=IN_PROGRESS|DONE}, see
 * {@code docs/business-rules.md}'s "Checkout workflow" table). This entity only proves out the
 * schema/read shape ahead of that design landing.
 */
@Entity
@Table(name = "saga_state")
public class SagaState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    @Column(nullable = false, length = 32)
    private String step;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SagaState() {
        // JPA
    }

    public SagaState(Long bookingId, String step, String status) {
        this.bookingId = bookingId;
        this.step = step;
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public String getStep() {
        return step;
    }

    public String getStatus() {
        return status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
