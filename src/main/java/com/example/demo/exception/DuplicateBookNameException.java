package com.example.demo.exception;

public class DuplicateBookNameException extends RuntimeException {
    public DuplicateBookNameException(String name) {
        super("Book name already exists: " + name);
    }
}
