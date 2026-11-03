package com.example.demo.service;

import com.example.demo.cache.BookCache;
import com.example.demo.dto.BookRequest;
import com.example.demo.entity.Author;
import com.example.demo.entity.Book;
import com.example.demo.kafka.BookEvent;
import com.example.demo.repository.AuthorRepository;
import com.example.demo.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceOutboxTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private AuthorRepository authorRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private BookCache bookCache;

    @Mock
    private TagService tagService;

    private BookService bookService;

    @BeforeEach
    void setUp() {
        bookService = new BookService(
                bookRepository,
                authorRepository,
                outboxService,
                bookCache,
                tagService);
    }

    @Test
    void createBookEnqueuesOutboxAfterSavingTheBook() {
        Author author = new Author("Frank", "US");
        author.setId(1L);
        when(bookRepository.existsByName("Dune")).thenReturn(false);
        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> {
            Book book = invocation.getArgument(0);
            book.setId(42L);
            return book;
        });

        BookRequest request = new BookRequest();
        request.setName("Dune");
        request.setAuthorId(1L);

        bookService.createBook(request);

        InOrder order = inOrder(bookRepository, outboxService);
        order.verify(bookRepository).save(any(Book.class));
        ArgumentCaptor<BookEvent> event = ArgumentCaptor.forClass(BookEvent.class);
        order.verify(outboxService).enqueue(event.capture());
        assertEquals(BookEvent.TYPE_CREATED, event.getValue().getType());
        assertEquals(42L, event.getValue().getBookId());
        assertEquals("Dune", event.getValue().getBookName());
        assertNull(event.getValue().getEventId());
    }

    @Test
    void deleteBookEnqueuesOutboxAfterDeletingTheBook() {
        when(bookRepository.existsById(42L)).thenReturn(true);

        bookService.deleteBook(42L);

        InOrder order = inOrder(bookRepository, outboxService);
        order.verify(bookRepository).deleteById(42L);
        ArgumentCaptor<BookEvent> event = ArgumentCaptor.forClass(BookEvent.class);
        order.verify(outboxService).enqueue(event.capture());
        assertEquals(BookEvent.TYPE_DELETED, event.getValue().getType());
        assertEquals(42L, event.getValue().getBookId());
    }
}
