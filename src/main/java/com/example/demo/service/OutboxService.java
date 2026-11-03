package com.example.demo.service;

import com.example.demo.entity.OutboxMessage;
import com.example.demo.kafka.BookEvent;
import com.example.demo.repository.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public OutboxService(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public void enqueue(BookEvent event) {
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID());
        }
        outboxRepository.save(new OutboxMessage(
                event.getEventId(),
                event.getType(),
                toJson(event)));
    }

    @Transactional
    public List<OutboxMessage> nextPending() {
        return outboxRepository.findTop20ByPublishedAtIsNullOrderByIdAsc();
    }

    @Transactional
    public void markPublished(Long id) {
        OutboxMessage message = outboxRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Outbox message not found: " + id));
        if (message.getPublishedAt() == null) {
            message.setPublishedAt(Instant.now());
        }
    }

    private String toJson(BookEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize book event", e);
        }
    }
}
