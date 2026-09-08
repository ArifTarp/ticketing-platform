package com.demo.ticketing.booking.application.event;

import com.demo.ticketing.messaging.event.BookingConfirmedEvent;

/** See {@link PaymentRequestedInternalEvent} for the publish-after-commit rationale. */
public record BookingConfirmedInternalEvent(BookingConfirmedEvent event) {
}
