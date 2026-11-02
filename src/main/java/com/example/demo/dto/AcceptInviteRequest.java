package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AcceptInviteRequest {

    @NotBlank(message = "token must not be blank")
    @Size(max = 128, message = "token must be at most 128 characters")
    private String token;

    @NotBlank(message = "username must not be blank")
    @Size(max = 50, message = "username must be at most 50 characters")
    private String username;

    @NotBlank(message = "password must not be blank")
    @Size(min = 4, max = 100, message = "password must be between 4 and 100 characters")
    private String password;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
