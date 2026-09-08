package com.demo.ticketing.notification.infra;

import com.demo.ticketing.notification.domain.Notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipient(String recipient);
}
