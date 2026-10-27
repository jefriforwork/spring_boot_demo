package com.example.demo.cache;

import com.example.demo.entity.Author;
import com.example.demo.entity.Book;

public class BookSnapshot {

    private Long id;
    private String name;
    private Long authorId;
    private String authorName;
    private String authorCountry;
    private boolean deleted;

    public BookSnapshot() {
    }

    public static BookSnapshot from(Book book) {
        Author author = book.getAuthor();
        BookSnapshot snapshot = new BookSnapshot();
        snapshot.setId(book.getId());
        snapshot.setName(book.getName());
        snapshot.setAuthorId(author.getId());
        snapshot.setAuthorName(author.getName());
        snapshot.setAuthorCountry(author.getCountry());
        snapshot.setDeleted(false);
        return snapshot;
    }

    public static BookSnapshot deleted(Long id) {
        BookSnapshot snapshot = new BookSnapshot();
        snapshot.setId(id);
        snapshot.setDeleted(true);
        return snapshot;
    }

    public Book toBook() {
        Author author = new Author(authorName, authorCountry);
        author.setId(authorId);
        Book book = new Book(name, author);
        book.setId(id);
        return book;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorCountry() {
        return authorCountry;
    }

    public void setAuthorCountry(String authorCountry) {
        this.authorCountry = authorCountry;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
