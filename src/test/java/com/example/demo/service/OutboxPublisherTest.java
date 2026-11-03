package com.example.demo.service;

import com.example.demo.entity.OutboxMessage;
import com.example.demo.kafka.KafkaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxService outboxService;

    @Mock
    private KafkaProducer kafkaProducer;

    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(outboxService, kafkaProducer);
    }

    @Test
    void marksTheRowPublishedOnlyAfterKafkaAcceptsIt() {
        OutboxMessage message = pending("{\"eventId\":\"abc\"}");
        when(outboxService.nextPending()).thenReturn(List.of(message));

        outboxPublisher.publishPending();

        InOrder order = inOrder(kafkaProducer, outboxService);
        order.verify(kafkaProducer).sendAndWait("{\"eventId\":\"abc\"}");
        order.verify(outboxService).markPublished(8L);
    }

    @Test
    void leavesTheRowUnpublishedWhenKafkaFails() {
        OutboxMessage message = pending("{\"eventId\":\"abc\"}");
        when(outboxService.nextPending()).thenReturn(List.of(message));
        doThrow(new IllegalStateException("broker down")).when(kafkaProducer).sendAndWait(message.getPayload());

        outboxPublisher.publishPending();

        verify(outboxService, never()).markPublished(8L);
    }

    private OutboxMessage pending(String payload) {
        OutboxMessage message = new OutboxMessage(UUID.randomUUID(), "BOOK_CREATED", payload);
        message.setId(8L);
        return message;
    }
}
