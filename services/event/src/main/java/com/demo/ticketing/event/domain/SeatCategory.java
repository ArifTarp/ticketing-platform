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

import java.math.BigDecimal;

/**
 * A price tier for one event. {@code section} is how the tier attaches to physical seats: every
 * {@link Seat} in that venue section is sold at this tier for this event (one tier per section per
 * event, enforced by a unique constraint).
 */
@Entity
@Table(name = "seat_categories")
public class SeatCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 32)
    private String section;

    protected SeatCategory() {
        // JPA
    }

    public SeatCategory(Event event, String name, BigDecimal price, String section) {
        this.event = event;
        this.name = name;
        this.price = price;
        this.section = section;
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getSection() {
        return section;
    }
}
