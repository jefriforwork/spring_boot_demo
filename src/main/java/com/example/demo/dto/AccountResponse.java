package com.example.demo.dto;

public class AccountResponse {

    private final String username;
    private final String role;

    public AccountResponse(String username, String role) {
        this.username = username;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }
}
