package com.demo.ticketing.payment.domain;

/**
 * Lifecycle of a mock {@link Payment}: {@code PENDING -> COMPLETED} or {@code PENDING -> FAILED},
 * decided synchronously by {@code PaymentMockRule} — there is no external PSP callback, so a
 * payment never actually rests in {@code PENDING} for longer than the current transaction.
 */
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED
}
