package com.demo.ticketing.booking.infra;

import com.demo.ticketing.booking.domain.Booking;
import com.demo.ticketing.booking.domain.BookingStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
