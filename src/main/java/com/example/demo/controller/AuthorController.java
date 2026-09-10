package com.example.demo.controller;

import com.example.demo.dto.AuthorRequest;
import com.example.demo.service.AuthorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/authors")
public class AuthorController {

    private final AuthorService authorService;

    public AuthorController(AuthorService authorService) {
        this.authorService = authorService;
    }

    @PostMapping
    public Map<String, Object> createAuthor(@Valid @RequestBody AuthorRequest request) {
        return authorService.createAuthor(request);
    }

    @GetMapping
    public Map<String, Object> getAllAuthors() {
        return authorService.getAllAuthors();
    }

    @GetMapping("/{id}")
    public Map<String, Object> getAuthorById(@PathVariable Long id) {
        return authorService.getAuthorById(id);
    }
}
