package com.example.demo.service;

import com.example.demo.config.InviteProperties;
import com.example.demo.dto.CreateInviteRequest;
import com.example.demo.dto.InviteResponse;
import com.example.demo.dto.ReadResponse;
import com.example.demo.entity.Invite;
import com.example.demo.entity.Role;
import com.example.demo.repository.InviteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class InviteService {

    private static final String PRIMARY = "primary (port 5432)";

    private final InviteRepository inviteRepository;
    private final InviteMailer inviteMailer;
    private final InviteProperties inviteProperties;

    public InviteService(
            InviteRepository inviteRepository,
            InviteMailer inviteMailer,
            InviteProperties inviteProperties) {
        this.inviteRepository = inviteRepository;
        this.inviteMailer = inviteMailer;
        this.inviteProperties = inviteProperties;
    }

    @Transactional
    public ReadResponse<InviteResponse> createInvite(CreateInviteRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String token = InviteTokens.newToken();
        Instant expiresAt = Instant.now().plus(inviteProperties.getTtl());
        Invite invite = new Invite(email, Role.valueOf(request.getRole()), InviteTokens.hash(token), expiresAt);
        invite = inviteRepository.saveAndFlush(invite);
        inviteMailer.send(invite.getEmail(), invite.getRole(), invite.getExpiresAt(), token);
        return new ReadResponse<>(PRIMARY, InviteResponse.from(invite));
    }
}
