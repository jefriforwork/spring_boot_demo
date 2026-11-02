package com.example.demo.service;

import com.example.demo.config.InviteProperties;
import com.example.demo.entity.Role;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.time.Instant;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InviteMailerTest {

    @Test
    void sendIncludesTokenRoleAndAcceptUrl() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        InviteProperties properties = new InviteProperties();
        properties.setFrom("noreply@demo.local");
        properties.setAcceptUrl("http://localhost:8080/api/v1/auth/accept-invite");
        properties.setTtl(Duration.ofHours(48));

        new InviteMailer(mailSender, properties).send(
                "person@example.com",
                Role.STAFF,
                Instant.parse("2026-11-04T00:00:00Z"),
                "raw-token");

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        String body = sent.getValue().getContent().toString();
        assertTrue(body.contains("raw-token"));
        assertTrue(body.contains("STAFF"));
        assertTrue(body.contains("http://localhost:8080/api/v1/auth/accept-invite"));
        assertEquals("person@example.com", sent.getValue().getAllRecipients()[0].toString());
        assertEquals("You are invited", sent.getValue().getSubject());
    }
}
