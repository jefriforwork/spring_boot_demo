package com.example.demo.kafka;

import com.example.demo.service.BookNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.core.JacksonException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerTest {

    @Mock
    private BookNotificationService bookNotificationService;

    private KafkaConsumer kafkaConsumer;

    @BeforeEach
    void setUp() {
        kafkaConsumer = new KafkaConsumer(bookNotificationService);
    }

    @Test
    void letsNotificationFailuresPropagateSoKafkaCanRetry() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(bookNotificationService).process(any());

        assertThrows(IllegalStateException.class, () -> kafkaConsumer.listen(createdEventJson()));
    }

    @Test
    void letsUnreadableMessagesPropagate() {
        assertThrows(JacksonException.class, () -> kafkaConsumer.listen("{"));
    }

    @Test
    void completesWhenTheNotificationWasAlreadySaved() {
        doThrow(new DataIntegrityViolationException("duplicate event"))
                .when(bookNotificationService).process(any());

        kafkaConsumer.listen(createdEventJson());

        verify(bookNotificationService).process(any());
    }

    private String createdEventJson() {
        return """
                {"eventId":"11111111-1111-1111-1111-111111111111","type":"BOOK_CREATED","bookId":1,"bookName":"Dune"}
                """;
    }
}
