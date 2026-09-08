package com.demo.ticketing.notification.application;

import com.demo.ticketing.notification.domain.Notification;
import com.demo.ticketing.notification.domain.NotificationChannel;
import com.demo.ticketing.notification.domain.NotificationStatus;
import com.demo.ticketing.notification.domain.NotificationType;
import com.demo.ticketing.notification.infra.NotificationRepository;
import com.demo.ticketing.notification.infra.ProcessedMessage;
import com.demo.ticketing.notification.infra.ProcessedMessageRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The plain (non-Kafka) business logic of turning a consumed {@code booking.events} payload into
 * a mock "sent" {@link Notification} row. Deliberately plain methods taking already-deserialized
 * event fields rather than a {@code @KafkaListener} itself, mirroring
 * {@code services/payment/.../PaymentProcessingService}, so the actual listener
 * (see {@code infra.kafka.BookingEventListener}) stays a thin delegator.
 *
 * <p>notification never publishes anything (root CLAUDE.md: "pure consumer... no inbound REST"),
 * so unlike payment/booking there is no publish-after-commit outbox step here — the
 * {@link Notification} row is written directly in the same transaction as the idempotency claim's
 * <em>caller</em> transaction (the claim itself still runs in its own {@code REQUIRES_NEW}
 * sub-transaction, see {@link #tryClaimMessage} for why).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public NotificationService(NotificationRepository notificationRepository,
                                ProcessedMessageRepository processedMessageRepository,
                                PlatformTransactionManager transactionManager) {
        this.notificationRepository = notificationRepository;
        this.processedMessageRepository = processedMessageRepository;
        // A dedicated REQUIRES_NEW template, not @Transactional(propagation = REQUIRES_NEW) on a
        // sibling method of this same bean: calling an @Transactional method from another method
        // on the same instance bypasses the Spring AOP proxy entirely (see
        // services/payment/.../PaymentProcessingService's identical javadoc for the full
        // explanation of this house convention). TransactionTemplate works regardless of call site.
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Processes one (already deduped-at-the-edge-or-not) {@code BookingConfirmedEvent}. Fully
     * idempotent on {@code messageId}: a redelivered event with the same {@code messageId} creates
     * no new {@link Notification} row.
     */
    @Transactional
    public void onBookingConfirmed(UUID messageId, Long bookingId, Long userId, List<Long> seatIds,
                                    Instant confirmedAt) {
        if (!tryClaimMessage(messageId)) {
            log.info("Duplicate BookingConfirmedEvent messageId={} bookingId={} — already processed, "
                    + "skipping (no new Notification row)", messageId, bookingId);
            return;
        }

        String recipient = recipientFor(userId);
        String payload = "Booking " + bookingId + " confirmed for user " + userId
                + ", seats " + seatIds + ", confirmedAt=" + confirmedAt;
        Notification notification = new Notification(
                NotificationType.BOOKING_CONFIRMED, recipient, NotificationChannel.EMAIL,
                NotificationStatus.SENT, payload);
        notificationRepository.save(notification);
        log.info("Mock-sent BOOKING_CONFIRMED notification to {} for bookingId={} messageId={}",
                recipient, bookingId, messageId);
    }

    /**
     * Processes one (already deduped-at-the-edge-or-not) {@code BookingCancelledEvent}. Fully
     * idempotent on {@code messageId}, same as {@link #onBookingConfirmed}.
     */
    @Transactional
    public void onBookingCancelled(UUID messageId, Long bookingId, Long userId, String reason,
                                    Instant cancelledAt) {
        if (!tryClaimMessage(messageId)) {
            log.info("Duplicate BookingCancelledEvent messageId={} bookingId={} — already processed, "
                    + "skipping (no new Notification row)", messageId, bookingId);
            return;
        }

        String recipient = recipientFor(userId);
        String payload = "Booking " + bookingId + " cancelled for user " + userId
                + ", reason=" + reason + ", cancelledAt=" + cancelledAt;
        Notification notification = new Notification(
                NotificationType.BOOKING_CANCELLED, recipient, NotificationChannel.EMAIL,
                NotificationStatus.SENT, payload);
        notificationRepository.save(notification);
        log.info("Mock-sent BOOKING_CANCELLED notification to {} for bookingId={} messageId={} reason={}",
                recipient, bookingId, messageId, reason);
    }

    /**
     * Synthesizes a plausible recipient address from {@code userId} alone — notification has no
     * real user-email lookup available and root CLAUDE.md forbids a cross-service DB read or
     * direct REST call to auth just to resolve one. Pure mock, per this service's CLAUDE.md.
     */
    private String recipientFor(Long userId) {
        return "user-" + userId + "@example.com";
    }

    /**
     * Attempts to claim {@code messageId} as newly-seen, returning {@code true} iff this call is
     * the one that gets to process it. See
     * {@code services/payment/.../PaymentProcessingService#tryClaimMessage}'s javadoc for the full
     * rationale (Postgres aborts the whole transaction on a failed statement, so the claim insert
     * must run in its own {@code REQUIRES_NEW} sub-transaction on its own connection, not be caught
     * inside the caller's own transaction) — this is now the house idempotency convention, reused
     * verbatim here.
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
