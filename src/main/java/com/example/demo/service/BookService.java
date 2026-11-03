package com.example.demo.service;

import com.example.demo.cache.BookCache;
import com.example.demo.cache.BookSnapshot;
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
import com.example.demo.kafka.BookEvent;
import com.example.demo.repository.AuthorRepository;
import com.example.demo.repository.BookRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService {

    private static final String PRIMARY = "primary (port 5432)";
    private static final String CACHE = "cache";

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final OutboxService outboxService;
    private final BookCache bookCache;
    private final TagService tagService;

    public BookService(
            BookRepository bookRepository,
            AuthorRepository authorRepository,
            OutboxService outboxService,
            BookCache bookCache,
            TagService tagService) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.outboxService = outboxService;
        this.bookCache = bookCache;
        this.tagService = tagService;
    }

    @Transactional
    public ReadResponse<BookResponse> createBook(BookRequest request) {
        if (bookRepository.existsByName(request.getName())) {
            throw new DuplicateBookNameException(request.getName());
        }
        Author author = findAuthor(request.getAuthorId());
        Book book = new Book(request.getName(), author);
        if (request.getTags() != null) {
            book.setTags(tagService.resolve(request.getTags()));
        }
        book = bookRepository.save(book);
        outboxService.enqueue(BookEvent.created(book.getId(), book.getName()));
        bookCache.putAfterCommit(book);
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
    public ReadResponse<BookResponse> getBookById(Long id) {
        BookSnapshot cached = bookCache.get(id);
        if (cached != null) {
            return toCachedResult(id, cached);
        }

        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book", id));
        if (!bookCache.putIfAbsent(book)) {
            BookSnapshot winner = bookCache.get(id);
            if (winner != null) {
                return toCachedResult(id, winner);
            }
        }
        return new ReadResponse<>(currentDataSource(), BookResponse.from(book));
    }

    @Transactional
    public ReadResponse<BookResponse> updateBook(Long id, BookRequest request) {
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
        bookCache.putAfterCommit(updated);
        return new ReadResponse<>(PRIMARY, BookResponse.from(updated));
    }

    @Transactional
    public MessageResponse deleteBook(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new ResourceNotFoundException("Book", id);
        }
        bookRepository.deleteById(id);
        outboxService.enqueue(BookEvent.deleted(id));
        bookCache.markDeletedAfterCommit(id);
        return new MessageResponse(PRIMARY, "Book deleted successfully");
    }

    private ReadResponse<BookResponse> toCachedResult(Long id, BookSnapshot cached) {
        if (cached.isDeleted()) {
            throw new ResourceNotFoundException("Book", id);
        }
        return new ReadResponse<>(CACHE, BookResponse.from(cached.toBook()));
    }

    private Author findAuthor(Long authorId) {
        return authorRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException("Author", authorId));
    }

    private String currentDataSource() {
        return DbContextHolder.isReadOnly() ? "replica (port 5433)" : PRIMARY;
    }
}
