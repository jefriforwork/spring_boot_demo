package com.example.demo.repository;

import com.example.demo.entity.BookNotification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookNotificationRepository extends JpaRepository<BookNotification, Long> {
}
