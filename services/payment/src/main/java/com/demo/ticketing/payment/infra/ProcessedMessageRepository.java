package com.demo.ticketing.payment.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, UUID> {
}
