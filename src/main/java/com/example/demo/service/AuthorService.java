package com.example.demo.service;

import com.example.demo.config.DbContextHolder;
import com.example.demo.dto.AuthorRequest;
import com.example.demo.entity.Author;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.AuthorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthorService {

    private final AuthorRepository authorRepository;
    private final TagService tagService;

    public AuthorService(AuthorRepository authorRepository, TagService tagService) {
        this.authorRepository = authorRepository;
        this.tagService = tagService;
    }

    @Transactional
    public Map<String, Object> createAuthor(AuthorRequest request) {
        Author author = new Author(request.getName(), request.getCountry());
        if (request.getTags() != null) {
            author.setTags(tagService.resolve(request.getTags()));
        }
        author = authorRepository.save(author);
        prepareForResponse(author);
        Map<String, Object> result = new HashMap<>();
        result.put("dataSource", "primary (port 5432)");
        result.put("data", author);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAllAuthors() {
        Map<String, Object> result = new HashMap<>();
        result.put("dataSource", DbContextHolder.isReadOnly() ? "replica (port 5433)" : "primary (port 5432)");
        List<Author> authors = authorRepository.findAll();
        authors.forEach(this::prepareForResponse);
        result.put("data", authors);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAuthorById(Long id) {
        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Author", id));
        prepareForResponse(author);
        Map<String, Object> result = new HashMap<>();
        result.put("dataSource", DbContextHolder.isReadOnly() ? "replica (port 5433)" : "primary (port 5432)");
        result.put("data", author);
        return result;
    }

    private void prepareForResponse(Author author) {
        author.getTags().size();
        author.getBooks().forEach(book -> book.getTags().size());
    }
}
