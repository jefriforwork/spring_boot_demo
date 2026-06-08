package com.example.demo.controller;

import com.example.demo.service.BookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @PostMapping("/books")
    public Map<String, Object> createBook(@RequestParam String name, @RequestParam String author) {
        return bookService.createBook(name, author);
    }

    @GetMapping("/books")
    public Map<String, Object> getAllBooks() {
        return bookService.getAllBooks();
    }
}
