package com.example.demo.service;

import com.example.demo.config.InviteProperties;
import com.example.demo.dto.CreateInviteRequest;
import com.example.demo.entity.Invite;
import com.example.demo.entity.Role;
import com.example.demo.repository.InviteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InviteServiceTest {

    @Mock
    private InviteRepository inviteRepository;

    @Mock
    private InviteMailer inviteMailer;

    private InviteService inviteService;

    @BeforeEach
    void setUp() {
        InviteProperties properties = new InviteProperties();
        properties.setTtl(Duration.ofHours(48));
        inviteService = new InviteService(inviteRepository, inviteMailer, properties);
    }

    @Test
    void createInviteStoresHashAndEmailsRawToken() {
        when(inviteRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Invite invite = invocation.getArgument(0);
            invite.setId(7L);
            return invite;
        });

        var response = inviteService.createInvite(request("Person@Example.com", "STAFF"));

        ArgumentCaptor<Invite> saved = ArgumentCaptor.forClass(Invite.class);
        verify(inviteRepository).saveAndFlush(saved.capture());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(inviteMailer).send(eq("person@example.com"), eq(Role.STAFF), eq(saved.getValue().getExpiresAt()), token.capture());

        assertEquals(InviteTokens.hash(token.getValue()), saved.getValue().getTokenHash());
        assertNotEquals(token.getValue(), saved.getValue().getTokenHash());
        assertEquals("person@example.com", response.getData().getEmail());
        assertEquals("STAFF", response.getData().getRole());
        assertEquals(7L, response.getData().getId());
    }

    @Test
    void createInvitePropagatesMailFailure() {
        when(inviteRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new MailSendException("smtp down")).when(inviteMailer)
                .send(any(), any(), any(), any());

        assertThrows(MailSendException.class, () -> inviteService.createInvite(request("person@example.com", "USER")));
    }

    private CreateInviteRequest request(String email, String role) {
        CreateInviteRequest request = new CreateInviteRequest();
        request.setEmail(email);
        request.setRole(role);
        return request;
    }
}
