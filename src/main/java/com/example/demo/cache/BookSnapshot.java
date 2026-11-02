package com.example.demo.cache;

import com.example.demo.entity.Author;
import com.example.demo.entity.Book;
import com.example.demo.entity.Tag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BookSnapshot {

    private Long id;
    private String name;
    private Long authorId;
    private String authorName;
    private String authorCountry;
    private List<String> authorTags = new ArrayList<>();
    private List<String> tags = new ArrayList<>();
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
        snapshot.setAuthorTags(namesOf(author.getTags()));
        snapshot.setTags(namesOf(book.getTags()));
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
        author.setTags(asTags(authorTags));
        Book book = new Book(name, author);
        book.setId(id);
        book.setTags(asTags(tags));
        return book;
    }

    private static List<String> namesOf(Set<Tag> tags) {
        if (tags == null || tags.isEmpty()) {
            return new ArrayList<>();
        }
        return tags.stream().map(Tag::getName).sorted().toList();
    }

    private static Set<Tag> asTags(List<String> names) {
        Set<Tag> tags = new LinkedHashSet<>();
        if (names == null) {
            return tags;
        }
        for (String name : names) {
            tags.add(new Tag(name));
        }
        return tags;
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

    public List<String> getAuthorTags() {
        return authorTags;
    }

    public void setAuthorTags(List<String> authorTags) {
        this.authorTags = authorTags;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
