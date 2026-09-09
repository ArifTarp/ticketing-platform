package com.demo.ticketing.booking.application;

import com.demo.ticketing.booking.infra.SagaStateRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the claim-before-work idempotency bug fixed in {@link
 * SagaCompletionService}: {@code onCompleted}/{@code onFailed} used to durably claim a Kafka
 * {@code messageId} (via a {@code REQUIRES_NEW} sub-transaction that commits immediately) *before*
 * running the booking-status/seat-mutation/{@code SagaState} work in the outer transaction. If that
 * later work threw, the outer transaction rolled back but the claim stayed committed, so redelivery
 * of the same message was silently skipped forever, leaving the booking stuck {@code PENDING} and
 * its seats stuck {@code HELD} with no recovery path.
 *
 * <p>This suite forces exactly that failure (via a {@link MockBean} {@link SagaStateRepository}
 * that throws on the final saga-state write) and proves: (1) the failed attempt leaves no
 * {@code processed_messages} row and rolls the booking back to {@code PENDING}, and (2) a
 * subsequent redelivery of the very same {@code messageId} is retried (not silently skipped) and
 * successfully completes the saga once the transient failure is gone.
 */
@Testcontainers
@SpringBootTest
class SagaCompletionServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private SagaCompletionService sagaCompletionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private SagaStateRepository sagaStateRepository;

    private long insertPendingBooking(long userId, long eventId, long seatId) {
        long bookingId = jdbcTemplate.queryForObject("""
                        INSERT INTO bookings (user_id, event_id, status, total, created_at, expires_at)
                        VALUES (?, ?, 'PENDING', ?, ?, ?)
                        RETURNING id
                        """,
                Long.class, userId, eventId, new BigDecimal("50.00"),
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now().plus(10, ChronoUnit.MINUTES)));
        jdbcTemplate.update(
                "INSERT INTO booking_items (booking_id, seat_id, price) VALUES (?, ?, ?)",
                bookingId, seatId, new BigDecimal("50.00"));
        jdbcTemplate.update(
                "INSERT INTO seat_availability (event_id, seat_id, status) VALUES (?, ?, 'HELD')",
                eventId, seatId);
        return bookingId;
    }

    private String bookingStatus(long bookingId) {
        return jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingId);
    }

    private String seatStatus(long eventId, long seatId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM seat_availability WHERE event_id = ? AND seat_id = ?",
                String.class, eventId, seatId);
    }

    private long processedMessageCount(UUID messageId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM processed_messages WHERE message_id = ?", Long.class, messageId);
    }

    @Test
    void onCompletedDoesNotClaimTheMessageWhenTheBusinessLogicFailsAndTheSameMessageCanBeRetried() {
        long eventId = 95_000L;
        long seatId = 95_001L;
        long bookingId = insertPendingBooking(1L, eventId, seatId);
        UUID messageId = UUID.randomUUID();

        // First delivery: the SagaState write blows up partway through the saga-completion work,
        // simulating a crash/transient failure after transitionFromPending+markSold already ran in
        // the same (not-yet-committed) transaction.
        when(sagaStateRepository.findByBookingId(bookingId)).thenReturn(java.util.Optional.empty());
        doThrow(new DataAccessException("simulated saga_state write failure") {
        }).when(sagaStateRepository).save(any());

        assertThatThrownBy(() -> sagaCompletionService.onCompleted(messageId, bookingId))
                .isInstanceOf(DataAccessException.class);

        // The whole outer transaction (including transitionFromPending and markSold) must have
        // rolled back -- nothing partially applied.
        assertThat(bookingStatus(bookingId)).isEqualTo("PENDING");
        assertThat(seatStatus(eventId, seatId)).isEqualTo("HELD");
        // The bug this fixes: the claim must NOT be durably committed when the business logic fails.
        assertThat(processedMessageCount(messageId)).isEqualTo(0L);

        // Second delivery of the SAME messageId, now that the transient failure is gone: with the
        // old claim-before-work bug this would be silently skipped forever (existsById already
        // true). With the fix, existsById is still false, so the redelivery is correctly reprocessed
        // and this time succeeds.
        Mockito.reset(sagaStateRepository);
        when(sagaStateRepository.findByBookingId(bookingId)).thenReturn(java.util.Optional.empty());

        sagaCompletionService.onCompleted(messageId, bookingId);

        assertThat(bookingStatus(bookingId)).isEqualTo("CONFIRMED");
        assertThat(seatStatus(eventId, seatId)).isEqualTo("SOLD");
        assertThat(processedMessageCount(messageId)).isEqualTo(1L);
    }

    @Test
    void onFailedDoesNotClaimTheMessageWhenTheBusinessLogicFailsAndTheSameMessageCanBeRetried() {
        long eventId = 95_100L;
        long seatId = 95_101L;
        long bookingId = insertPendingBooking(2L, eventId, seatId);
        UUID messageId = UUID.randomUUID();

        when(sagaStateRepository.findByBookingId(bookingId)).thenReturn(java.util.Optional.empty());
        doThrow(new DataAccessException("simulated saga_state write failure") {
        }).when(sagaStateRepository).save(any());

        assertThatThrownBy(() -> sagaCompletionService.onFailed(messageId, bookingId, "PAYMENT_FAILED"))
                .isInstanceOf(DataAccessException.class);

        assertThat(bookingStatus(bookingId)).isEqualTo("PENDING");
        assertThat(seatStatus(eventId, seatId)).isEqualTo("HELD");
        assertThat(processedMessageCount(messageId)).isEqualTo(0L);

        Mockito.reset(sagaStateRepository);
        when(sagaStateRepository.findByBookingId(bookingId)).thenReturn(java.util.Optional.empty());

        sagaCompletionService.onFailed(messageId, bookingId, "PAYMENT_FAILED");

        assertThat(bookingStatus(bookingId)).isEqualTo("CANCELLED");
        assertThat(seatStatus(eventId, seatId)).isEqualTo("AVAILABLE");
        assertThat(processedMessageCount(messageId)).isEqualTo(1L);
    }
}
