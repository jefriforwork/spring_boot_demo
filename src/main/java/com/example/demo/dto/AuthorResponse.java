package com.example.demo.dto;

import com.example.demo.entity.Author;

public class AuthorResponse {

    private Long id;
    private String name;
    private String country;

    public AuthorResponse() {
    }

    public AuthorResponse(Long id, String name, String country) {
        this.id = id;
        this.name = name;
        this.country = country;
    }

    public static AuthorResponse from(Author author) {
        return new AuthorResponse(author.getId(), author.getName(), author.getCountry());
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCountry() {
        return country;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}
