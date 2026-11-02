package com.example.demo.controller;

import com.example.demo.dto.CreateInviteRequest;
import com.example.demo.dto.InviteResponse;
import com.example.demo.dto.ReadResponse;
import com.example.demo.service.InviteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invites")
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @PostMapping
    public ReadResponse<InviteResponse> createInvite(@Valid @RequestBody CreateInviteRequest request) {
        return inviteService.createInvite(request);
    }
}
