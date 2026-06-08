package com.example.demo.controller;

import com.example.demo.dto.BookRequest;
import com.example.demo.service.BookService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @PostMapping
    public Map<String, Object> createBook(@Valid @RequestBody BookRequest request) {
        return bookService.createBook(request);
    }

    @GetMapping
    public Map<String, Object> getAllBooks() {
        return bookService.getAllBooks();
    }

    @GetMapping("/{id}")
    public Map<String, Object> getBookById(@PathVariable Long id) {
        return bookService.getBookById(id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateBook(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        return bookService.updateBook(id, request);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteBook(@PathVariable Long id) {
        return bookService.deleteBook(id);
    }
}
