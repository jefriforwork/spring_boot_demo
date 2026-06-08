package com.example.demo.service;

import com.example.demo.config.DbContextHolder;
import com.example.demo.dto.BookRequest;
import com.example.demo.entity.Book;
import com.example.demo.exception.DuplicateBookNameException;
import com.example.demo.exception.ResourceNotFoundException;
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
    public Map<String, Object> createBook(BookRequest request) {
        if (bookRepository.existsByName(request.getName())) {
            throw new DuplicateBookNameException(request.getName());
        }
        Book book = bookRepository.save(new Book(request.getName(), request.getAuthor()));
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

    @Transactional(readOnly = true)
    @Cacheable(value = "books", key = "#id")
    public Map<String, Object> getBookById(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book", id));
        Map<String, Object> result = new HashMap<>();
        result.put("dataSource", DbContextHolder.isReadOnly() ? "replica (port 5433)" : "primary (port 5432)");
        result.put("data", book);
        return result;
    }

    @Transactional
    @CacheEvict(value = "books", allEntries = true)
    public Map<String, Object> updateBook(Long id, BookRequest request) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book", id));

        if (!book.getName().equals(request.getName()) && bookRepository.existsByName(request.getName())) {
            throw new DuplicateBookNameException(request.getName());
        }

        book.setName(request.getName());
        book.setAuthor(request.getAuthor());
        Book updated = bookRepository.save(book);

        Map<String, Object> result = new HashMap<>();
        result.put("dataSource", "primary (port 5432)");
        result.put("data", updated);
        return result;
    }

    @Transactional
    @CacheEvict(value = "books", allEntries = true)
    public Map<String, Object> deleteBook(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new ResourceNotFoundException("Book", id);
        }
        bookRepository.deleteById(id);
        Map<String, Object> result = new HashMap<>();
        result.put("dataSource", "primary (port 5432)");
        result.put("message", "Book deleted successfully");
        return result;
    }
}