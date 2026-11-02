package com.example.demo.dto;

import com.example.demo.entity.Book;
import com.example.demo.entity.Tag;

import java.util.List;
import java.util.Set;

public class BookResponse {

    private Long id;
    private String name;
    private AuthorResponse author;
    private List<String> tags;

    public BookResponse() {
    }

    public BookResponse(Long id, String name, AuthorResponse author) {
        this(id, name, author, List.of());
    }

    public BookResponse(Long id, String name, AuthorResponse author, List<String> tags) {
        this.id = id;
        this.name = name;
        this.author = author;
        this.tags = tags;
    }

    public static BookResponse from(Book book) {
        return new BookResponse(
                book.getId(),
                book.getName(),
                AuthorResponse.from(book.getAuthor()),
                namesOf(book.getTags()));
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

    public AuthorResponse getAuthor() {
        return author;
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

    public void setAuthor(AuthorResponse author) {
        this.author = author;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
