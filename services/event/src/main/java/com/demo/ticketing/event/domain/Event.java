package com.demo.ticketing.event.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EventStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Price tiers for this event, most expensive first (matches the price-tier list in the UI). */
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("price DESC")
    private List<SeatCategory> seatCategories = new ArrayList<>();

    protected Event() {
        // JPA
    }

    public Event(Venue venue, String title, String description, Instant startsAt, EventStatus status) {
        this.venue = venue;
        this.title = title;
        this.description = description;
        this.startsAt = startsAt;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Venue getVenue() {
        return venue;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public EventStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<SeatCategory> getSeatCategories() {
        return seatCategories;
    }

    public void addSeatCategory(SeatCategory seatCategory) {
        this.seatCategories.add(seatCategory);
    }

    /**
     * business-rules.md: only an {@code ON_SALE} event starting in the future can be booked. The
     * booking service enforces this for itself; here it drives what the catalog offers/labels.
     */
    public boolean isBookable(Instant now) {
        return status == EventStatus.ON_SALE && startsAt.isAfter(now);
    }
}
