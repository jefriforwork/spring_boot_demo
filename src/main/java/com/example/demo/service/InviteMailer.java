package com.example.demo.service;

import com.example.demo.config.InviteProperties;
import com.example.demo.entity.Role;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class InviteMailer {

    private final JavaMailSender mailSender;
    private final InviteProperties inviteProperties;

    public InviteMailer(JavaMailSender mailSender, InviteProperties inviteProperties) {
        this.mailSender = mailSender;
        this.inviteProperties = inviteProperties;
    }

    public void send(String email, Role role, Instant expiresAt, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(inviteProperties.getFrom());
            helper.setTo(email);
            helper.setSubject("You are invited");
            helper.setText("""
                    You are invited to demo as %s.
                    This invite expires at %s.

                    Accept it once with POST %s
                    {"token":"%s","username":"your-name","password":"your-password"}

                    The role is already stored with this invite. The request does not accept a role.
                    """.formatted(role.name(), expiresAt, inviteProperties.getAcceptUrl(), token));
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new MailSendException("Could not build invite email", e);
        }
    }
}
