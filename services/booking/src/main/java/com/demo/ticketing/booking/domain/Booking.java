package com.demo.ticketing.booking.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One customer's attempt to buy a set of seats for one event. Booking is the sole owner/mutator of
 * both {@code Booking.status} and {@link SeatAvailability#getStatus()} (business-rules.md,
 * "Checkout workflow" preamble) — this entity only models the read shape for Phase 7; the saga
 * (Phase 8) and the Redis-backed hold endpoint (also Phase 7, {@code saga-orchestrator}) are the
 * only code paths allowed to transition {@link #status}.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookingStatus status;

    /** Sum of {@link BookingItem#getPrice()} at hold time — never recalculated (business-rules.md). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<BookingItem> items = new ArrayList<>();

    protected Booking() {
        // JPA
    }

    public Booking(Long userId, Long eventId, BigDecimal total, Instant expiresAt) {
        this.userId = userId;
        this.eventId = eventId;
        this.status = BookingStatus.PENDING;
        this.total = total;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getEventId() {
        return eventId;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public List<BookingItem> getItems() {
        return items;
    }

    /** {@code item} must already reference this booking (see {@link BookingItem} constructor). */
    public void addItem(BookingItem item) {
        this.items.add(item);
    }

    /**
     * Recomputes {@link #total} as the sum of all current {@link #items}' prices. Used by
     * {@code BookingHoldService} (ADR-0004) when appending newly-held seats to an existing
     * {@code PENDING} booking, since {@link #total} is otherwise only ever set once at
     * construction time.
     */
    public void recalculateTotal() {
        this.total = items.stream().map(BookingItem::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Pushes {@link #expiresAt} out to {@code candidate} if it is later than the current value.
     * Used when appending a fresh 10-minute hold to an existing booking (ADR-0004) so the whole
     * booking's expiry reflects the most recently held seat, never a stale earlier deadline.
     */
    public void extendExpiry(Instant candidate) {
        if (candidate.isAfter(this.expiresAt)) {
            this.expiresAt = candidate;
        }
    }
}
