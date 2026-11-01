package com.example.demo.dto;

import java.util.List;

public class ValidationErrorResponse {

    private final int status;
    private final List<String> errors;

    public ValidationErrorResponse(int status, List<String> errors) {
        this.status = status;
        this.errors = errors;
    }

    public int getStatus() {
        return status;
    }

    public List<String> getErrors() {
        return errors;
    }
}
