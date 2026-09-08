package com.demo.ticketing.payment.infra;

import com.demo.ticketing.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByBookingId(Long bookingId);
}
