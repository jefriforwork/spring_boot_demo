package com.example.demo.repository;

import com.example.demo.entity.Author;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    @Override
    @EntityGraph(attributePaths = "tags")
    Page<Author> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "tags")
    Optional<Author> findById(Long id);
}
