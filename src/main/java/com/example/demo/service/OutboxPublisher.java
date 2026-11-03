package com.example.demo.service;

import com.example.demo.entity.OutboxMessage;
import com.example.demo.kafka.KafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxService outboxService;
    private final KafkaProducer kafkaProducer;

    public OutboxPublisher(OutboxService outboxService, KafkaProducer kafkaProducer) {
        this.outboxService = outboxService;
        this.kafkaProducer = kafkaProducer;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:1000}")
    public void publishPending() {
        for (OutboxMessage message : outboxService.nextPending()) {
            publish(message);
        }
    }

    private void publish(OutboxMessage message) {
        try {
            kafkaProducer.sendAndWait(message.getPayload());
            outboxService.markPublished(message.getId());
        } catch (RuntimeException e) {
            log.warn("Outbox message {} stays unpublished and will be retried", message.getId(), e);
        }
    }
}
