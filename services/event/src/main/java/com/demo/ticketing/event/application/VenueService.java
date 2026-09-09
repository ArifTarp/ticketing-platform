package com.demo.ticketing.event.application;

import com.demo.ticketing.event.domain.Venue;
import com.demo.ticketing.event.infra.VenueRepository;
import com.demo.ticketing.event.web.dto.CreateVenueRequest;
import com.demo.ticketing.event.web.dto.VenueResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin write use case for venues (roadmap Phase 14). The gateway enforces {@code ADMIN} on this
 * route before the request reaches this service — see services/event/CLAUDE.md's
 * "Not here (deliberately)" note, no local security/JWT here.
 */
@Service
public class VenueService {

    private final VenueRepository venueRepository;

    public VenueService(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    @Transactional
    public VenueResponse createVenue(CreateVenueRequest request) {
        Venue venue = new Venue(request.name(), request.address(), request.city());
        Venue saved = venueRepository.save(venue);
        return new VenueResponse(saved.getId(), saved.getName(), saved.getAddress(), saved.getCity());
    }
}
