package com.demo.ticketing.payment.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Config for the mock payment rule (services/payment/CLAUDE.md: "no real PSP" — a deterministic
 * amount threshold stands in for a real gateway's success/decline decision).
 *
 * @param failThreshold amounts {@code >= failThreshold} are mocked as {@code FAILED};
 *                       everything below succeeds. See {@code application.yml}'s
 *                       {@code payment.mock.fail-threshold}.
 */
@ConfigurationProperties(prefix = "payment.mock")
public record PaymentMockProperties(BigDecimal failThreshold) {

    public PaymentMockProperties {
        if (failThreshold == null) {
            failThreshold = new BigDecimal("500.00");
        }
    }
}
