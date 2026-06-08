package com.example.demo.service;

import com.example.demo.config.DbContextHolder;
import com.example.demo.entity.Book;
import com.example.demo.exception.DuplicateBookNameException;
import com.example.demo.repository.BookRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Transactional
    @CacheEvict(value = "books", allEntries = true)
    public Map<String, Object> createBook(String name, String author) {
        if (bookRepository.existsByName(name)) {
            throw new DuplicateBookNameException(name);
        }
        Book book = bookRepository.save(new Book(name, author));
        Map<String, Object> result = new HashMap<>();
        result.put("dataSource", "primary (port 5432)");
        result.put("data", book);
        return result;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "books", key = "'all'")
    public Map<String, Object> getAllBooks() {
        List<Book> books = bookRepository.findAll();
        Map<String, Object> result = new HashMap<>();
        result.put("dataSource", DbContextHolder.isReadOnly() ? "replica (port 5433)" : "primary (port 5432)");
        result.put("data", books);
        return result;
    }
}