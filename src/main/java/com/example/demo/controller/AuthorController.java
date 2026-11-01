package com.example.demo.controller;

import com.example.demo.dto.AuthorPageResponse;
import com.example.demo.dto.AuthorRequest;
import com.example.demo.dto.AuthorResponse;
import com.example.demo.dto.ReadResponse;
import com.example.demo.service.AuthorService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/authors")
public class AuthorController {

    private final AuthorService authorService;

    public AuthorController(AuthorService authorService) {
        this.authorService = authorService;
    }

    @PostMapping
    public ReadResponse<AuthorResponse> createAuthor(@Valid @RequestBody AuthorRequest request) {
        return authorService.createAuthor(request);
    }

    @GetMapping
    public AuthorPageResponse getAllAuthors(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,asc") String[] sort) {

        Sort.Direction direction = Sort.Direction.fromString(sort[1]);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort[0]));
        return authorService.getAllAuthors(pageable);
    }

    @GetMapping("/{id}")
    public ReadResponse<AuthorResponse> getAuthorById(@PathVariable Long id) {
        return authorService.getAuthorById(id);
    }
}
