package com.demo.ticketing.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One mock payment attempt for a booking. This service has no real PSP integration
 * (services/payment/CLAUDE.md) — {@link PaymentMockRule} in the application layer decides
 * {@link PaymentStatus#COMPLETED}/{@link PaymentStatus#FAILED} synchronously based on the amount.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "provider_ref", length = 64)
    private String providerRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Payment() {
        // JPA
    }

    public Payment(Long bookingId, BigDecimal amount, PaymentStatus status) {
        this.bookingId = bookingId;
        this.amount = amount;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Transitions this payment to {@link PaymentStatus#COMPLETED} with a synthesized provider ref. */
    public void markCompleted(String providerRef) {
        this.status = PaymentStatus.COMPLETED;
        this.providerRef = providerRef;
    }

    /** Transitions this payment to {@link PaymentStatus#FAILED}. No provider ref for a mock failure. */
    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }
}
