package com.demo.ticketing.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A physical seat in a venue — pure static layout, reused by every event held there.
 *
 * <p>ADR-0001: this entity has no availability/sold/held state and must never gain one. Whether a
 * seat can be bought for a given event lives in the booking service's {@code seat_availability}.
 */
@Entity
@Table(name = "seats")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false, length = 32)
    private String section;

    /** "row" is a reserved word in SQL/HQL, so the column is {@code row_label}. */
    @Column(name = "row_label", nullable = false, length = 16)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private Integer number;

    protected Seat() {
        // JPA
    }

    public Seat(Venue venue, String section, String rowLabel, Integer number) {
        this.venue = venue;
        this.section = section;
        this.rowLabel = rowLabel;
        this.number = number;
    }

    public Long getId() {
        return id;
    }

    public Venue getVenue() {
        return venue;
    }

    public String getSection() {
        return section;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public Integer getNumber() {
        return number;
    }
}
