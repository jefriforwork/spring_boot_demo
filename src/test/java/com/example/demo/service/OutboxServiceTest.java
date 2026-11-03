package com.example.demo.service;

import com.example.demo.entity.OutboxMessage;
import com.example.demo.kafka.BookEvent;
import com.example.demo.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxRepository outboxRepository;

    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new OutboxService(outboxRepository);
    }

    @Test
    void enqueueStoresTheEventJsonAndAssignsAnEventId() throws Exception {
        BookEvent event = BookEvent.created(42L, "Dune");

        outboxService.enqueue(event);

        ArgumentCaptor<OutboxMessage> saved = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(saved.capture());
        assertNotNull(saved.getValue().getEventId());
        assertEquals(BookEvent.TYPE_CREATED, saved.getValue().getEventType());
        assertNull(saved.getValue().getPublishedAt());

        ObjectMapper objectMapper = JsonMapper.builder().build();
        BookEvent stored = objectMapper.readValue(saved.getValue().getPayload(), BookEvent.class);
        assertEquals(saved.getValue().getEventId(), stored.getEventId());
        assertEquals(42L, stored.getBookId());
        assertEquals("Dune", stored.getBookName());
    }

    @Test
    void markPublishedSetsTheTimestampOnce() {
        OutboxMessage message = new OutboxMessage(
                BookEvent.created(7L, "Dune").getEventId(),
                BookEvent.TYPE_CREATED,
                "{}");
        message.setId(3L);
        when(outboxRepository.findById(3L)).thenReturn(Optional.of(message));

        outboxService.markPublished(3L);
        Instant publishedAt = message.getPublishedAt();
        outboxService.markPublished(3L);

        assertNotNull(publishedAt);
        assertEquals(publishedAt, message.getPublishedAt());
    }
}
