package com.demo.ticketing.notification.domain;

/**
 * This is a pure mock (root CLAUDE.md: "notification — pure consumer... 'sends' mail/SMS (mock)")
 * — there is no real provider that could report back a delivery failure, so {@link #SENT} is the
 * only value that ever gets written.
 */
public enum NotificationStatus {
    SENT
}
