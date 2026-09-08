package com.demo.ticketing.payment.application;

import com.demo.ticketing.payment.domain.Payment;
import com.demo.ticketing.payment.domain.PaymentStatus;
import com.demo.ticketing.payment.infra.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Business-logic behaviour of {@link PaymentProcessingService} against the real Flyway schema in
 * a throwaway Postgres, mirroring services/booking's Testcontainers pattern
 * (BookingControllerTest). No Kafka involved — this only exercises the plain method a future
 * {@code @KafkaListener} will call.
 */
@Testcontainers
@SpringBootTest
class PaymentProcessingServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PaymentProcessingService paymentProcessingService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void freshMessageWithLowAmountCompletesThePayment() {
        long bookingId = 1001L;
        BigDecimal amount = new BigDecimal("49.99");

        PaymentOutcome outcome = paymentProcessingService.process(UUID.randomUUID(), bookingId, amount);

        assertThat(outcome.duplicate()).isFalse();
        assertThat(outcome.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(outcome.providerRef()).startsWith("mock-");
        assertThat(outcome.reason()).isNull();

        List<Payment> payments = paymentRepository.findByBookingId(bookingId);
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payments.get(0).getProviderRef()).isEqualTo(outcome.providerRef());
    }

    @Test
    void freshMessageWithAmountAtOrAboveThresholdFailsThePayment() {
        long bookingId = 1002L;
        BigDecimal amount = new BigDecimal("500.00");

        PaymentOutcome outcome = paymentProcessingService.process(UUID.randomUUID(), bookingId, amount);

        assertThat(outcome.duplicate()).isFalse();
        assertThat(outcome.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(outcome.providerRef()).isNull();
        assertThat(outcome.reason()).contains("500.00").contains("exceeds");

        List<Payment> payments = paymentRepository.findByBookingId(bookingId);
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void replayingTheSameMessageIdIsANoOpAndCreatesNoSecondPaymentRow() {
        long bookingId = 1003L;
        BigDecimal amount = new BigDecimal("10.00");
        UUID messageId = UUID.randomUUID();

        PaymentOutcome first = paymentProcessingService.process(messageId, bookingId, amount);
        assertThat(first.duplicate()).isFalse();

        PaymentOutcome replay = paymentProcessingService.process(messageId, bookingId, amount);

        assertThat(replay.duplicate()).isTrue();
        assertThat(replay.bookingId()).isNull();
        assertThat(replay.amount()).isNull();
        assertThat(replay.status()).isNull();
        assertThat(replay.providerRef()).isNull();
        assertThat(replay.reason()).isNull();

        List<Payment> payments = paymentRepository.findByBookingId(bookingId);
        assertThat(payments).hasSize(1);
    }
}
