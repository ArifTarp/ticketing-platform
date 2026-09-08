package com.demo.ticketing.payment.application;

import com.demo.ticketing.messaging.event.PaymentCompletedEvent;
import com.demo.ticketing.messaging.event.PaymentFailedEvent;
import com.demo.ticketing.payment.application.event.PaymentCompletedInternalEvent;
import com.demo.ticketing.payment.application.event.PaymentFailedInternalEvent;
import com.demo.ticketing.payment.domain.Payment;
import com.demo.ticketing.payment.domain.PaymentStatus;
import com.demo.ticketing.payment.infra.PaymentRepository;
import com.demo.ticketing.payment.infra.ProcessedMessage;
import com.demo.ticketing.payment.infra.ProcessedMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The plain (non-Kafka) business logic of mock payment processing. Deliberately a plain method
 * taking already-deserialized command fields rather than a {@code @KafkaListener} itself, so a
 * future consumer (services/payment/CLAUDE.md's Phase 8 wiring) can call {@link #process} directly
 * after deserializing {@code PaymentRequestedCommand}, then decide what to publish from the
 * returned {@link PaymentOutcome}.
 */
@Service
public class PaymentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingService.class);

    private final PaymentRepository paymentRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final PaymentMockRule paymentMockRule;
    private final PaymentMockProperties paymentMockProperties;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public PaymentProcessingService(PaymentRepository paymentRepository,
                                     ProcessedMessageRepository processedMessageRepository,
                                     PaymentMockRule paymentMockRule,
                                     PaymentMockProperties paymentMockProperties,
                                     ApplicationEventPublisher applicationEventPublisher,
                                     PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.processedMessageRepository = processedMessageRepository;
        this.paymentMockRule = paymentMockRule;
        this.paymentMockProperties = paymentMockProperties;
        this.applicationEventPublisher = applicationEventPublisher;
        // A dedicated REQUIRES_NEW template, not @Transactional(propagation = REQUIRES_NEW) on a
        // sibling method of this same bean: calling an @Transactional method from another method
        // on the same instance bypasses the Spring AOP proxy entirely (same self-invocation pitfall
        // booking's BookingHoldService avoids with its own TransactionTemplate — see
        // services/booking/CLAUDE.md). TransactionTemplate works regardless of call site.
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Processes one (already deduped-at-the-edge-or-not) {@code PaymentRequestedCommand}. Fully
     * idempotent on {@code messageId}: a redelivered command with the same {@code messageId}
     * returns {@link PaymentOutcome#duplicate()} and creates no new {@link Payment} row.
     *
     * @param messageId the command's own message id — the dedup key, NOT the payment id.
     * @param bookingId the booking to pay for.
     * @param amount    the amount to (mock-)charge.
     * @return see {@link PaymentOutcome}'s javadoc for exactly which fields are populated when.
     */
    @Transactional
    public PaymentOutcome process(UUID messageId, Long bookingId, BigDecimal amount) {
        if (processedMessageRepository.existsById(messageId)) {
            log.info("Duplicate PaymentRequestedCommand messageId={} bookingId={} — already processed, "
                    + "skipping (no new Payment row, no event to publish)", messageId, bookingId);
            return PaymentOutcome.alreadyProcessed();
        }

        Payment payment = new Payment(bookingId, amount, PaymentStatus.PENDING);
        paymentRepository.save(payment);

        PaymentOutcome outcome;
        if (paymentMockRule.shouldFail(amount)) {
            payment.markFailed();
            paymentRepository.save(payment);
            String reason = "amount " + amount + " exceeds mock fail-threshold "
                    + paymentMockProperties.failThreshold();
            log.info("Mock payment FAILED bookingId={} amount={} messageId={}", bookingId, amount, messageId);
            PaymentFailedEvent event = new PaymentFailedEvent(UUID.randomUUID(), bookingId, reason, Instant.now());
            applicationEventPublisher.publishEvent(new PaymentFailedInternalEvent(event));
            outcome = PaymentOutcome.failed(bookingId, amount, reason);
        } else {
            String providerRef = "mock-" + UUID.randomUUID();
            payment.markCompleted(providerRef);
            paymentRepository.save(payment);
            log.info("Mock payment COMPLETED bookingId={} amount={} providerRef={} messageId={}",
                    bookingId, amount, providerRef, messageId);
            PaymentCompletedEvent event =
                    new PaymentCompletedEvent(UUID.randomUUID(), bookingId, providerRef, amount, Instant.now());
            applicationEventPublisher.publishEvent(new PaymentCompletedInternalEvent(event));
            outcome = PaymentOutcome.completed(bookingId, amount, providerRef);
        }

        // Claim-after-work, not claim-before-work (see class-level ADR-0005 reference and
        // #claimMessageAfterCommit's javadoc): the messageId is only durably marked "processed"
        // once we know the Payment row + outbox event above have actually committed.
        claimMessageAfterCommit(messageId, bookingId);
        return outcome;
    }

    /**
     * Registers the actual idempotency claim to run only <b>after</b> {@link #process}'s outer
     * transaction has committed (see {@code docs/adr/0005-...idempotency...md}: "the claim must
     * only be committed once the rest of the unit of work is known to succeed"). We cannot simply
     * call {@link #tryClaimMessage} inline at the end of {@link #process}'s method body and be done
     * with it: {@link #process} is itself the {@code @Transactional} boundary, so the outer
     * transaction's actual commit only happens <i>after</i> the method body returns, via the Spring
     * AOP proxy — a claim inserted "at the end of the method" would still race the outer commit, not
     * follow it. Instead we hook the outer transaction's {@code afterCommit} synchronization
     * callback, which Spring guarantees runs only once that outer transaction has durably committed
     * (mirrors {@code KafkaOutboxPublisher}'s {@code @TransactionalEventListener(AFTER_COMMIT)}
     * pattern for the same reason — publish/claim only after the local write is safe).
     *
     * <p><b>Accepted tradeoff:</b> if the process crashes in the (very small) window between the
     * outer transaction's commit and this {@code afterCommit} callback actually running the claim
     * insert, the message will be reprocessed on redelivery (since {@code existsById} will still say
     * "not seen"). That reprocessing attempt will try to insert a second {@link Payment} row for the
     * same {@code bookingId} and fail on the {@code payments.booking_id} unique constraint, which
     * aborts that redelivery loudly (visible retry/DLQ) rather than silently leaving the original
     * message a permanently-claimed zombie with nothing published — which is exactly the bug this
     * fix replaces. Claim-after-work is preferred over claim-before-work precisely because this
     * failure mode is loud and narrow, whereas the old failure mode was silent and unbounded (the
     * booking would sit orphaned until the hold-expiry sweep).
     */
    private void claimMessageAfterCommit(UUID messageId, Long bookingId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (!tryClaimMessage(messageId)) {
                    log.warn("Post-commit idempotency claim for messageId={} bookingId={} lost a race — "
                            + "a concurrent redelivery of the same messageId already claimed it after this "
                            + "call's business work had already committed", messageId, bookingId);
                }
            }
        });
    }

    /**
     * Attempts to claim {@code messageId} as newly-seen, returning {@code true} iff this call is
     * the one that gets to process it.
     *
     * <p><b>Why not a plain {@code save()} + catch inside the caller's own transaction:</b> Hibernate
     * defers the actual {@code INSERT} to flush time, so a naive
     * {@code try { repo.save(...) } catch (DataIntegrityViolationException e) { ... }} inside
     * {@link #process} would usually not throw at the call site at all — the violation would only
     * surface later, at the transaction's implicit flush/commit, by which point the {@code Payment}
     * writes below would already be queued in the same flush and would be lost/rolled back too.
     * Forcing a flush (e.g. {@code saveAndFlush}) fixes the *timing*, but on Postgres a failed
     * statement aborts the entire current transaction/connection ("current transaction is aborted,
     * commands ignored until end of transaction block") — every subsequent statement on that same
     * connection fails until a rollback, which would corrupt the rest of this method's work even
     * though we "handled" the exception in Java.
     *
     * <p>The fix used here: run the claim attempt in its own {@code REQUIRES_NEW} transaction, on
     * its own physical connection. If the insert loses the id-uniqueness race, only that isolated
     * sub-transaction aborts and rolls back — the caller's outer transaction (which creates the
     * {@link Payment}) is on a different connection and is completely unaffected. An
     * {@code existsById} fast path is checked first purely to avoid paying for an exception in the
     * common case of a genuine replay; the {@code REQUIRES_NEW} insert is what actually makes this
     * race-safe against a concurrent redelivery of the same message.
     */
    private boolean tryClaimMessage(UUID messageId) {
        if (processedMessageRepository.existsById(messageId)) {
            return false;
        }
        try {
            Boolean claimed = requiresNewTransactionTemplate.execute(status -> {
                processedMessageRepository.saveAndFlush(new ProcessedMessage(messageId));
                return true;
            });
            return Boolean.TRUE.equals(claimed);
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}
