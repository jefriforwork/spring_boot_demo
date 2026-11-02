package com.example.demo.dto;

import com.example.demo.entity.Author;
import com.example.demo.entity.Tag;

import java.util.List;
import java.util.Set;

public class AuthorResponse {

    private Long id;
    private String name;
    private String country;
    private List<String> tags;

    public AuthorResponse() {
    }

    public AuthorResponse(Long id, String name, String country) {
        this(id, name, country, List.of());
    }

    public AuthorResponse(Long id, String name, String country, List<String> tags) {
        this.id = id;
        this.name = name;
        this.country = country;
        this.tags = tags;
    }

    public static AuthorResponse from(Author author) {
        return new AuthorResponse(author.getId(), author.getName(), author.getCountry(), namesOf(author.getTags()));
    }

    private static List<String> namesOf(Set<Tag> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream().map(Tag::getName).sorted().toList();
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

    public List<String> getTags() {
        return tags;
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

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
