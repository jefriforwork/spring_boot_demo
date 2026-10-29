package com.example.demo.service;

import com.example.demo.cache.BookCache;
import com.example.demo.cache.BookSnapshot;
import com.example.demo.config.DbContextHolder;
import com.example.demo.dto.BookRequest;
import com.example.demo.entity.Author;
import com.example.demo.entity.Book;
import com.example.demo.exception.DuplicateBookNameException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.kafka.BookEvent;
import com.example.demo.kafka.KafkaProducer;
import com.example.demo.repository.AuthorRepository;
import com.example.demo.repository.BookRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final KafkaProducer kafkaProducer;
    private final BookCache bookCache;
    private final TagService tagService;

    public BookService(BookRepository bookRepository, AuthorRepository authorRepository, KafkaProducer kafkaProducer, BookCache bookCache, TagService tagService) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.kafkaProducer = kafkaProducer;
        this.bookCache = bookCache;
        this.tagService = tagService;
    }

    @Transactional
    public Map<String, Object> createBook(BookRequest request) {
        if (bookRepository.existsByName(request.getName())) {
            throw new DuplicateBookNameException(request.getName());
        }
        Author author = findAuthor(request.getAuthorId());
        Book book = new Book(request.getName(), author);
        if (request.getTags() != null) {
            book.setTags(tagService.resolve(request.getTags()));
        }
        book = bookRepository.save(book);
        prepareForResponse(book);
        kafkaProducer.send(BookEvent.created(book.getId(), book.getName()));
        bookCache.putAfterCommit(book);
        Map<String, Object> result = new HashMap<>();
        result.put("dataSource", "primary (port 5432)");
        result.put("data", book);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAllBooks(Pageable pageable, String country, String keyword) {
        String safeKeyword = keyword == null ? "" : keyword;
        Page<Book> page;
        if (country != null && !country.isBlank()) {
            page = bookRepository.findByAuthor_CountryIgnoreCaseAndNameContainingIgnoreCase(
                    country, safeKeyword, pageable);
        } else if (keyword != null && !keyword.isBlank()) {
            page = bookRepository.findByNameContainingIgnoreCase(keyword, pageable);
        } else {
            page = bookRepository.findAll(pageable);
        }
        return toPageResult(page);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBookById(Long id) {
        BookSnapshot cached = bookCache.get(id);
        if (cached != null) {
            return toCachedResult(id, cached);
        }

        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book", id));
        prepareForResponse(book);
        if (!bookCache.putIfAbsent(book)) {
            BookSnapshot winner = bookCache.get(id);
            if (winner != null) {
                return toCachedResult(id, winner);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dataSource", DbContextHolder.isReadOnly() ? "replica (port 5433)" : "primary (port 5432)");
        result.put("data", book);
        return result;
    }

    @Transactional
    public Map<String, Object> updateBook(Long id, BookRequest request) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book", id));

        if (!book.getName().equals(request.getName()) && bookRepository.existsByName(request.getName())) {
            throw new DuplicateBookNameException(request.getName());
        }

        book.setName(request.getName());
        book.setAuthor(findAuthor(request.getAuthorId()));
        if (request.getTags() != null) {
            book.getTags().clear();
            book.getTags().addAll(tagService.resolve(request.getTags()));
        }
        Book updated = bookRepository.save(book);
        prepareForResponse(updated);
        bookCache.putAfterCommit(updated);

        Map<String, Object> result = new HashMap<>();
        result.put("dataSource", "primary (port 5432)");
        result.put("data", updated);
        return result;
    }

    @Transactional
    public Map<String, Object> deleteBook(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new ResourceNotFoundException("Book", id);
        }
        bookRepository.deleteById(id);
        kafkaProducer.send(BookEvent.deleted(id));
        bookCache.markDeletedAfterCommit(id);
        Map<String, Object> result = new HashMap<>();
        result.put("dataSource", "primary (port 5432)");
        result.put("message", "Book deleted successfully");
        return result;
    }

    private void prepareForResponse(Book book) {
        book.getTags().size();
        book.getAuthor().getTags().size();
    }

    private Map<String, Object> toCachedResult(Long id, BookSnapshot cached) {
        if (cached.isDeleted()) {
            throw new ResourceNotFoundException("Book", id);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("dataSource", "cache");
        result.put("data", cached.toBook());
        return result;
    }

    private Author findAuthor(Long authorId) {
        return authorRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException("Author", authorId));
    }

    private Map<String, Object> toPageResult(Page<Book> page) {
        Map<String, Object> result = new HashMap<>();
        result.put("dataSource", DbContextHolder.isReadOnly() ? "replica (port 5433)" : "primary (port 5432)");
        result.put("data", page.getContent());
        result.put("pagination", Map.of(
                "page", page.getNumber(),
                "size", page.getSize(),
                "totalElements", page.getTotalElements(),
                "totalPages", page.getTotalPages(),
                "first", page.isFirst(),
                "last", page.isLast()
        ));
        return result;
    }
}
