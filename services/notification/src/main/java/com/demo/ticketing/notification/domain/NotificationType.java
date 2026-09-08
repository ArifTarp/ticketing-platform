package com.demo.ticketing.notification.domain;

/** What triggered this notification, mirroring the two {@code booking.events} payload types. */
public enum NotificationType {
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED
}
