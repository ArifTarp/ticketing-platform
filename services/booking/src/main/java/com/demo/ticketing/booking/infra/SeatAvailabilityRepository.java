package com.demo.ticketing.booking.infra;

import com.demo.ticketing.booking.domain.SeatAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeatAvailabilityRepository extends JpaRepository<SeatAvailability, Long> {

    /** Backs the future {@code GET /api/v1/bookings/availability?eventId=} read endpoint. */
    List<SeatAvailability> findByEventId(Long eventId);

    Optional<SeatAvailability> findByEventIdAndSeatId(Long eventId, Long seatId);
}
