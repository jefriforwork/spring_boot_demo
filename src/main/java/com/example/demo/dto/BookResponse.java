package com.example.demo.dto;

import com.example.demo.entity.Book;

public class BookResponse {

    private Long id;
    private String name;
    private AuthorResponse author;

    public BookResponse() {
    }

    public BookResponse(Long id, String name, AuthorResponse author) {
        this.id = id;
        this.name = name;
        this.author = author;
    }

    public static BookResponse from(Book book) {
        return new BookResponse(book.getId(), book.getName(), AuthorResponse.from(book.getAuthor()));
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

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAuthor(AuthorResponse author) {
        this.author = author;
    }
}
