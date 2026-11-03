package com.example.demo.service;

import com.example.demo.kafka.BookEvent;
import com.example.demo.repository.BookNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookNotificationServiceTest {

    @Mock
    private BookNotificationRepository bookNotificationRepository;

    private BookNotificationService bookNotificationService;

    @BeforeEach
    void setUp() {
        bookNotificationService = new BookNotificationService(bookNotificationRepository, 0);
    }

    @Test
    void skipsAnEventThatWasAlreadySaved() {
        UUID eventId = UUID.randomUUID();
        BookEvent event = BookEvent.created(42L, "Dune");
        event.setEventId(eventId);
        when(bookNotificationRepository.existsByEventId(eventId)).thenReturn(true);

        bookNotificationService.process(event);

        verify(bookNotificationRepository, never()).save(any());
    }

    @Test
    void savesANewEvent() {
        UUID eventId = UUID.randomUUID();
        BookEvent event = BookEvent.created(42L, "Dune");
        event.setEventId(eventId);
        when(bookNotificationRepository.existsByEventId(eventId)).thenReturn(false);

        bookNotificationService.process(event);

        verify(bookNotificationRepository).save(any());
    }
}
