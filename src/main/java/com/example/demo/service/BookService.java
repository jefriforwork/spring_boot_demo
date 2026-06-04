package com.example.demo.service;

import com.example.demo.entity.Book;
import com.example.demo.repository.BookRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public Book createBook(String name, String author) {
        return bookRepository.save(new Book(name, author));
    }

    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }
}