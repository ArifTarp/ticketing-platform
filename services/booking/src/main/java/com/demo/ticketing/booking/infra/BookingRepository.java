package com.demo.ticketing.booking.infra;

import com.demo.ticketing.booking.domain.Booking;
import com.demo.ticketing.booking.domain.BookingStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Fetch-joins {@code items} so {@code BookingMapper} never triggers lazy-loading N+1 queries.
     * Named distinctly from {@link #findById(Object)} (inherited, no fetch join) so callers opt in
     * explicitly.
     */
    @EntityGraph(attributePaths = "items")
    Optional<Booking> findWithItemsById(Long id);

    @EntityGraph(attributePaths = "items")
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "items")
    List<Booking> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, BookingStatus status);

    /** Backs {@code GET /api/v1/bookings?userId=&eventId=} (no {@code status} filter). */
    @EntityGraph(attributePaths = "items")
    List<Booking> findByUserIdAndEventIdOrderByCreatedAtDesc(Long userId, Long eventId);

    /**
     * Backs {@code GET /api/v1/bookings?userId=&eventId=&status=} — in particular
     * {@code status=PENDING}, which the frontend uses on page refresh to resume an in-progress
     * hold/checkout for one event (see {@link #findFirstByUserIdAndEventIdAndStatus} below: the
     * same {@code (userId, eventId, PENDING)} invariant that method relies on guarantees this
     * query returns at most one row for {@code status=PENDING}).
     */
    @EntityGraph(attributePaths = "items")
    List<Booking> findByUserIdAndEventIdAndStatusOrderByCreatedAtDesc(Long userId, Long eventId, BookingStatus status);

    /**
     * Backs ADR-0004: {@code BookingHoldService.holdSeats} appends to this booking (if present)
     * instead of always creating a new one for the same {@code (userId, eventId)} pair.
     */
    @EntityGraph(attributePaths = "items")
    Optional<Booking> findFirstByUserIdAndEventIdAndStatus(Long userId, Long eventId, BookingStatus status);

    /** Backs the Phase 8 hold-expiry sweep (indexed by {@code ix_bookings_status_expires_at}). */
    List<Booking> findByStatusAndExpiresAtBefore(BookingStatus status, Instant now);

    /**
     * Atomic race guard (Phase 8): only transitions a booking out of {@code PENDING} if it is
     * still {@code PENDING} at the moment of the update. Returns the number of rows updated -- 0
     * means another caller (the payment-result consumer or the timeout sweep) already terminated
     * this booking first, and the caller must treat that as a no-op (skip seat mutation, skip
     * {@code SagaState} update, skip publishing) rather than double-processing.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Booking b SET b.status = :newStatus WHERE b.id = :id AND b.status = :pending")
    int transitionFromPendingInternal(@Param("id") Long id,
                                       @Param("newStatus") BookingStatus newStatus,
                                       @Param("pending") BookingStatus pending);

    /** Convenience wrapper binding {@code pending = BookingStatus.PENDING} for callers. */
    default int transitionFromPending(Long id, BookingStatus newStatus) {
        return transitionFromPendingInternal(id, newStatus, BookingStatus.PENDING);
    }
}
