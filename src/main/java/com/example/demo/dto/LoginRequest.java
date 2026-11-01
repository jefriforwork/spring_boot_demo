package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {

    @NotBlank(message = "username must not be blank")
    @Size(max = 50, message = "username must be at most 50 characters")
    private String username;

    @NotBlank(message = "password must not be blank")
    @Size(max = 100, message = "password must be at most 100 characters")
    private String password;

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
