package com.example.demo.dto;

import com.example.demo.entity.BookNotification;

import java.time.Instant;

public class BookNotificationResponse {

    private final Long id;
    private final String eventType;
    private final Long bookId;
    private final String bookName;
    private final String message;
    private final Instant createdAt;

    public BookNotificationResponse(
            Long id,
            String eventType,
            Long bookId,
            String bookName,
            String message,
            Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.bookId = bookId;
        this.bookName = bookName;
        this.message = message;
        this.createdAt = createdAt;
    }

    public static BookNotificationResponse from(BookNotification notification) {
        return new BookNotificationResponse(
                notification.getId(),
                notification.getEventType(),
                notification.getBookId(),
                notification.getBookName(),
                notification.getMessage(),
                notification.getCreatedAt());
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public Long getBookId() {
        return bookId;
    }

    public String getBookName() {
        return bookName;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
