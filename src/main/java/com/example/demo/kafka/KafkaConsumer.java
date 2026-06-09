package com.example.demo.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaConsumer {

    @KafkaListener(topics = "book-events", groupId = "book-group")
    public void listen(String message) {
        System.out.println("[Kafka Consumer] 收到訊息: " + message);
    }
}
