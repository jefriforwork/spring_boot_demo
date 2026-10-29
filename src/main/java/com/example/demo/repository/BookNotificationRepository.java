package com.example.demo.repository;

import com.example.demo.entity.BookNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookNotificationRepository extends JpaRepository<BookNotification, Long> {

    List<BookNotification> findAllByOrderByIdDesc();
}
