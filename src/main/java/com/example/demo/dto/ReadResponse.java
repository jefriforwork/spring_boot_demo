package com.example.demo.dto;

public class ReadResponse<T> {

    private String dataSource;
    private T data;

    public ReadResponse() {
    }

    public ReadResponse(String dataSource, T data) {
        this.dataSource = dataSource;
        this.data = data;
    }

    public String getDataSource() {
        return dataSource;
    }

    public T getData() {
        return data;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public void setData(T data) {
        this.data = data;
    }
}
