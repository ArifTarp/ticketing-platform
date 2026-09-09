package com.demo.ticketing.event.infra;

import com.demo.ticketing.event.domain.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueRepository extends JpaRepository<Venue, Long> {
}
