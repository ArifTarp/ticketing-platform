package com.demo.ticketing.booking.domain;

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
 * One held/sold seat within a {@link Booking}. {@code price} is a snapshot taken at hold time —
 * never recalculated from the event service's current price tier (business-rules.md).
 */
@Entity
@Table(name = "booking_items")
public class BookingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    protected BookingItem() {
        // JPA
    }

    public BookingItem(Booking booking, Long seatId, BigDecimal price) {
        this.booking = booking;
        this.seatId = seatId;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public Long getSeatId() {
        return seatId;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
