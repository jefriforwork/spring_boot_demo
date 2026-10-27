package com.example.demo.repository;

import com.example.demo.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {
    boolean existsByName(String name);

    Optional<Book> findByName(String name);

    @Override
    @EntityGraph(attributePaths = {"author", "author.tags", "tags"})
    Page<Book> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"author", "author.tags", "tags"})
    Optional<Book> findById(Long id);

    @EntityGraph(attributePaths = {"author", "author.tags", "tags"})
    Page<Book> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "author.tags", "tags"})
    Page<Book> findByAuthor_CountryIgnoreCaseAndNameContainingIgnoreCase(
            String country, String name, Pageable pageable);
}
