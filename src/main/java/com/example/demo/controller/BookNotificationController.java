package com.example.demo.controller;

import com.example.demo.service.BookNotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/book-notifications")
public class BookNotificationController {

    private final BookNotificationService bookNotificationService;

    public BookNotificationController(BookNotificationService bookNotificationService) {
        this.bookNotificationService = bookNotificationService;
    }

    @GetMapping
    public Map<String, Object> getAllNotifications() {
        return bookNotificationService.getAllNotifications();
    }
}
