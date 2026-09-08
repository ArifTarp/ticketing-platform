package com.demo.ticketing.payment.application;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The mock success/failure decision (services/payment/CLAUDE.md: "no real PSP, no inbound REST
 * endpoint by design"). Deliberately trivial and deterministic so the checkout saga's failure path
 * (booking's {@code PaymentFailedEvent} handling) is exercisable on demand in tests/demos simply by
 * choosing an amount at or above the configured threshold.
 */
@Component
public class PaymentMockRule {

    private final PaymentMockProperties properties;

    public PaymentMockRule(PaymentMockProperties properties) {
        this.properties = properties;
    }

    /** @return {@code true} if {@code amount} should be mocked as a failed payment. */
    public boolean shouldFail(BigDecimal amount) {
        return amount.compareTo(properties.failThreshold()) >= 0;
    }
}
