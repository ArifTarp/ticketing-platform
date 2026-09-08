package com.demo.ticketing.booking.infra;

import com.demo.ticketing.booking.domain.SagaState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SagaStateRepository extends JpaRepository<SagaState, Long> {

    Optional<SagaState> findByBookingId(Long bookingId);
}
