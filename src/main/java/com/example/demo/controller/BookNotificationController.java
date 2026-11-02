package com.example.demo.controller;

import com.example.demo.dto.BookNotificationPageResponse;
import com.example.demo.service.BookNotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/book-notifications")
public class BookNotificationController {

    private final BookNotificationService bookNotificationService;

    public BookNotificationController(BookNotificationService bookNotificationService) {
        this.bookNotificationService = bookNotificationService;
    }

    @GetMapping
    public BookNotificationPageResponse getAllNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return bookNotificationService.getAllNotifications(pageable);
    }
}
