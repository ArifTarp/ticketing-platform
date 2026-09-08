package com.demo.ticketing.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @EnableScheduling} backs {@code HoldExpirySweepService}'s timeout sweep (Phase 8). */
@EnableScheduling
@SpringBootApplication
public class BookingApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookingApplication.class, args);
    }
}
