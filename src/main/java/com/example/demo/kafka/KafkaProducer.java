package com.example.demo.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class KafkaProducer {

    public static final String BOOK_EVENTS_TOPIC = "book-events";
    public static final String BOOK_EVENTS_DLT_TOPIC = BOOK_EVENTS_TOPIC + ".DLT";

    private static final long ACK_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendAndWait(String payload) {
        try {
            kafkaTemplate.send(BOOK_EVENTS_TOPIC, payload).get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing to Kafka", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to publish to Kafka", e);
        }
    }
}
