package com.example.demo.repository;

import com.example.demo.entity.BookNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BookNotificationRepository extends JpaRepository<BookNotification, Long> {

    boolean existsByEventId(UUID eventId);
}
