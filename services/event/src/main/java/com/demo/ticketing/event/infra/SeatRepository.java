package com.demo.ticketing.event.infra;

import com.demo.ticketing.event.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    /**
     * The venue's full static layout, in render order. {@code rowLabel} sorts as text, which is what
     * we want for labels like "A"/"B"; venues with more than 9 numeric rows would need natural sort.
     */
    List<Seat> findByVenueIdOrderBySectionAscRowLabelAscNumberAsc(Long venueId);
}
