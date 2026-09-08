package com.demo.ticketing.booking.application.event;

import com.demo.ticketing.messaging.event.BookingCancelledEvent;

/** See {@link PaymentRequestedInternalEvent} for the publish-after-commit rationale. */
public record BookingCancelledInternalEvent(BookingCancelledEvent event) {
}
