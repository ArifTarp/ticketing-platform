package com.demo.ticketing.booking.domain;

/**
 * Lifecycle per root {@code CLAUDE.md} and {@code docs/business-rules.md}:
 * {@code PENDING -> CONFIRMED}, {@code PENDING -> CANCELLED}, {@code PENDING -> EXPIRED}.
 * A booking never leaves a terminal state ({@code CONFIRMED}/{@code CANCELLED}/{@code EXPIRED}).
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    EXPIRED
}
