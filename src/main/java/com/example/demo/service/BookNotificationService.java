package com.example.demo.service;

import com.example.demo.config.DbContextHolder;
import com.example.demo.entity.BookNotification;
import com.example.demo.kafka.BookEvent;
import com.example.demo.repository.BookNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BookNotificationService {

    private static final Logger log = LoggerFactory.getLogger(BookNotificationService.class);

    private final BookNotificationRepository bookNotificationRepository;
    private final long processingDelayMs;

    public BookNotificationService(
            BookNotificationRepository bookNotificationRepository,
            @Value("${app.kafka.notification-delay-ms:500}") long processingDelayMs) {
        this.bookNotificationRepository = bookNotificationRepository;
        this.processingDelayMs = processingDelayMs;
    }

    @Transactional
    public void process(BookEvent event) {
        sleepToSimulateSlowWork();
        BookNotification notification = new BookNotification(
                event.getType(),
                event.getBookId(),
                event.getBookName(),
                event.toMessage());
        bookNotificationRepository.save(notification);
        log.info("Load leveling processed {} for bookId={}", event.getType(), event.getBookId());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAllNotifications() {
        List<BookNotification> notifications = bookNotificationRepository.findAllByOrderByIdDesc();
        Map<String, Object> result = new HashMap<>();
        result.put("dataSource", DbContextHolder.isReadOnly() ? "replica (port 5433)" : "primary (port 5432)");
        result.put("count", notifications.size());
        result.put("data", notifications);
        return result;
    }

    private void sleepToSimulateSlowWork() {
        if (processingDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(processingDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating slow notification work", e);
        }
    }
}
