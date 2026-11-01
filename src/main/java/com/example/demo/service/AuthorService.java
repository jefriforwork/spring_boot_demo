package com.example.demo.service;

import com.example.demo.config.DbContextHolder;
import com.example.demo.dto.AuthorPageResponse;
import com.example.demo.dto.AuthorRequest;
import com.example.demo.dto.AuthorResponse;
import com.example.demo.dto.ReadResponse;
import com.example.demo.entity.Author;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.AuthorRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorService {

    private static final String PRIMARY = "primary (port 5432)";

    private final AuthorRepository authorRepository;

    public AuthorService(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    @Transactional
    public ReadResponse<AuthorResponse> createAuthor(AuthorRequest request) {
        Author author = authorRepository.save(new Author(request.getName(), request.getCountry()));
        return new ReadResponse<>(PRIMARY, AuthorResponse.from(author));
    }

    @Transactional(readOnly = true)
    public AuthorPageResponse getAllAuthors(Pageable pageable) {
        return AuthorPageResponse.from(authorRepository.findAll(pageable), currentDataSource());
    }

    @Transactional(readOnly = true)
    public ReadResponse<AuthorResponse> getAuthorById(Long id) {
        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Author", id));
        return new ReadResponse<>(currentDataSource(), AuthorResponse.from(author));
    }

    private String currentDataSource() {
        return DbContextHolder.isReadOnly() ? "replica (port 5433)" : PRIMARY;
    }
}
