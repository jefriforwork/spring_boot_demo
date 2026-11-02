package com.example.demo.dto;

import com.example.demo.entity.Invite;

import java.time.Instant;

public class InviteResponse {

    private final Long id;
    private final String email;
    private final String role;
    private final Instant expiresAt;

    public InviteResponse(Long id, String email, String role, Instant expiresAt) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.expiresAt = expiresAt;
    }

    public static InviteResponse from(Invite invite) {
        return new InviteResponse(invite.getId(), invite.getEmail(), invite.getRole().name(), invite.getExpiresAt());
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
