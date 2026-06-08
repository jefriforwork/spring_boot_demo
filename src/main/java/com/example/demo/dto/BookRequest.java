package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class BookRequest {

    @NotBlank(message = "name must not be blank")
    @Size(max = 255, message = "name must be at most 255 characters")
    private String name;

    @NotBlank(message = "author must not be blank")
    @Size(max = 255, message = "author must be at most 255 characters")
    private String author;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }
}
