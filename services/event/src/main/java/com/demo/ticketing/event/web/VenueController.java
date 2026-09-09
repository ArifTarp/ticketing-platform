package com.demo.ticketing.event.web;

import com.demo.ticketing.event.application.VenueService;
import com.demo.ticketing.event.web.dto.CreateVenueRequest;
import com.demo.ticketing.event.web.dto.VenueResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin write endpoint (roadmap Phase 14). The gateway is responsible for rejecting non-ADMIN
 * callers before the request reaches here — see services/event/CLAUDE.md's
 * "Not here (deliberately)" note; this controller does not itself check any role/JWT.
 */
@RestController
@RequestMapping("/api/v1/venues")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    public ResponseEntity<VenueResponse> createVenue(@Valid @RequestBody CreateVenueRequest request) {
        VenueResponse response = venueService.createVenue(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
