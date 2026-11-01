package com.example.demo.dto;

public class MessageResponse {

    private final String dataSource;
    private final String message;

    public MessageResponse(String dataSource, String message) {
        this.dataSource = dataSource;
        this.message = message;
    }

    public String getDataSource() {
        return dataSource;
    }

    public String getMessage() {
        return message;
    }
}
