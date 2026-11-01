package com.example.demo.service;

import com.example.demo.config.DbContextHolder;
import com.example.demo.dto.BookPageResponse;
import com.example.demo.dto.BookRequest;
import com.example.demo.dto.BookResponse;
import com.example.demo.dto.MessageResponse;
import com.example.demo.dto.ReadResponse;
import com.example.demo.entity.Author;
import com.example.demo.entity.Book;
import com.example.demo.exception.DuplicateBookNameException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.kafka.KafkaProducer;
import com.example.demo.repository.AuthorRepository;
import com.example.demo.repository.BookRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService {

    private static final String PRIMARY = "primary (port 5432)";

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final KafkaProducer kafkaProducer;

    public BookService(BookRepository bookRepository, AuthorRepository authorRepository, KafkaProducer kafkaProducer) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.kafkaProducer = kafkaProducer;
    }

    @Transactional
    @CacheEvict(value = "books", allEntries = true)
    public ReadResponse<BookResponse> createBook(BookRequest request) {
        if (bookRepository.existsByName(request.getName())) {
            throw new DuplicateBookNameException(request.getName());
        }
        Author author = findAuthor(request.getAuthorId());
        Book book = bookRepository.save(new Book(request.getName(), author));
        kafkaProducer.send("book-events", "新書上架: " + book.getName());
        return new ReadResponse<>(PRIMARY, BookResponse.from(book));
    }

    @Transactional(readOnly = true)
    public BookPageResponse getAllBooks(Pageable pageable, String country, String keyword) {
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
        return BookPageResponse.from(page, currentDataSource());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "books", key = "#id")
    public ReadResponse<BookResponse> getBookById(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book", id));
        return new ReadResponse<>(currentDataSource(), BookResponse.from(book));
    }

    @Transactional
    @CacheEvict(value = "books", allEntries = true)
    public ReadResponse<BookResponse> updateBook(Long id, BookRequest request) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book", id));

        if (!book.getName().equals(request.getName()) && bookRepository.existsByName(request.getName())) {
            throw new DuplicateBookNameException(request.getName());
        }

        book.setName(request.getName());
        book.setAuthor(findAuthor(request.getAuthorId()));
        Book updated = bookRepository.save(book);
        return new ReadResponse<>(PRIMARY, BookResponse.from(updated));
    }

    @Transactional
    @CacheEvict(value = "books", allEntries = true)
    public MessageResponse deleteBook(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new ResourceNotFoundException("Book", id);
        }
        bookRepository.deleteById(id);
        kafkaProducer.send("book-events", "書本已刪除: id=" + id);
        return new MessageResponse(PRIMARY, "Book deleted successfully");
    }

    private Author findAuthor(Long authorId) {
        return authorRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException("Author", authorId));
    }

    private String currentDataSource() {
        return DbContextHolder.isReadOnly() ? "replica (port 5433)" : PRIMARY;
    }
}
