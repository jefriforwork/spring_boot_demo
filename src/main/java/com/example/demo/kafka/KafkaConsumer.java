package com.example.demo.kafka;

import com.example.demo.service.BookNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class KafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumer.class);

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final BookNotificationService bookNotificationService;

    public KafkaConsumer(BookNotificationService bookNotificationService) {
        this.bookNotificationService = bookNotificationService;
    }

    @KafkaListener(topics = KafkaProducer.BOOK_EVENTS_TOPIC, groupId = "book-group")
    public void listen(String message) {
        try {
            BookEvent event = objectMapper.readValue(message, BookEvent.class);
            bookNotificationService.process(event);
        } catch (Exception e) {
            log.warn("Skipping unreadable book event: {}", message, e);
        }
    }
}
