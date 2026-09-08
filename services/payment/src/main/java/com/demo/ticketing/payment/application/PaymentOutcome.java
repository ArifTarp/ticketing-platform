package com.demo.ticketing.payment.application;

import java.math.BigDecimal;

/**
 * Result of {@link PaymentProcessingService#process}, designed to be consumed by a future
 * {@code @KafkaListener} to decide whether/what to publish to {@code payment.events}.
 *
 * <p>Exactly one of {@link #duplicate()} being {@code true}, or a non-null {@link #status()}
 * ({@code COMPLETED}/{@code FAILED}), applies:
 * <ul>
 *   <li>{@link #duplicate()} {@code true} — this {@code messageId} was already processed
 *       (redelivered command). {@link #bookingId()}/{@link #amount()}/{@link #status()}/
 *       {@link #providerRef()}/{@link #reason()} are all {@code null}. The caller must publish
 *       nothing — the original processing already published (or will publish) the event.</li>
 *   <li>{@link #duplicate()} {@code false} and {@link #status()} is {@code COMPLETED} — a fresh
 *       {@link com.demo.ticketing.payment.domain.Payment} was created and completed.
 *       {@link #providerRef()} is populated (e.g. {@code "mock-<uuid>"}); {@link #reason()} is
 *       {@code null}. The caller should publish {@code PaymentCompletedEvent}.</li>
 *   <li>{@link #duplicate()} {@code false} and {@link #status()} is {@code FAILED} — a fresh
 *       payment was created and failed the mock rule. {@link #reason()} is populated (a
 *       human-readable explanation); {@link #providerRef()} is {@code null}. The caller should
 *       publish {@code PaymentFailedEvent}.</li>
 * </ul>
 *
 * @param duplicate   {@code true} if {@code messageId} had already been processed; nothing new
 *                    happened and no event should be published.
 * @param bookingId   the booking this payment is for; {@code null} when {@link #duplicate()}.
 * @param amount      the payment amount; {@code null} when {@link #duplicate()}.
 * @param status      {@code COMPLETED} or {@code FAILED} for a fresh payment; {@code null} when
 *                    {@link #duplicate()}.
 * @param providerRef synthesized mock provider reference, populated only when {@code status ==
 *                    COMPLETED}.
 * @param reason      human-readable failure reason, populated only when {@code status == FAILED}.
 */
public record PaymentOutcome(
        boolean duplicate,
        Long bookingId,
        BigDecimal amount,
        com.demo.ticketing.payment.domain.PaymentStatus status,
        String providerRef,
        String reason
) {

    public static PaymentOutcome alreadyProcessed() {
        return new PaymentOutcome(true, null, null, null, null, null);
    }

    public static PaymentOutcome completed(Long bookingId, BigDecimal amount, String providerRef) {
        return new PaymentOutcome(false, bookingId, amount,
                com.demo.ticketing.payment.domain.PaymentStatus.COMPLETED, providerRef, null);
    }

    public static PaymentOutcome failed(Long bookingId, BigDecimal amount, String reason) {
        return new PaymentOutcome(false, bookingId, amount,
                com.demo.ticketing.payment.domain.PaymentStatus.FAILED, null, reason);
    }
}
