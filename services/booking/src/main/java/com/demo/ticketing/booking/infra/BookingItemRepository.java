package com.demo.ticketing.booking.infra;

import com.demo.ticketing.booking.domain.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {

    List<BookingItem> findByBookingId(Long bookingId);
}
