package com.demo.ticketing.event.domain;

/**
 * Lifecycle per {@code docs/business-rules.md}: {@code DRAFT -> ON_SALE -> SOLD_OUT | CLOSED}.
 * Only an {@code ON_SALE} event whose {@code startsAt} is in the future can be booked.
 */
public enum EventStatus {
    DRAFT,
    ON_SALE,
    SOLD_OUT,
    CLOSED
}
