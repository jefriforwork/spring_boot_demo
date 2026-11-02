package com.example.demo.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class KafkaProducer {

    public static final String BOOK_EVENTS_TOPIC = "book-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public KafkaProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(BookEvent event) {
        try {
            kafkaTemplate.send(BOOK_EVENTS_TOPIC, objectMapper.writeValueAsString(event));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize book event", e);
        }
    }
}
