package com.example.demo.dto;

import com.example.demo.entity.Book;
import org.springframework.data.domain.Page;

import java.util.List;

public class BookPageResponse {

    private final String dataSource;
    private final List<BookResponse> data;
    private final PageInfo pagination;

    public BookPageResponse(String dataSource, List<BookResponse> data, PageInfo pagination) {
        this.dataSource = dataSource;
        this.data = data;
        this.pagination = pagination;
    }

    public static BookPageResponse from(Page<Book> page, String dataSource) {
        List<BookResponse> books = page.getContent().stream().map(BookResponse::from).toList();
        return new BookPageResponse(dataSource, books, PageInfo.from(page));
    }

    public String getDataSource() {
        return dataSource;
    }

    public List<BookResponse> getData() {
        return data;
    }

    public PageInfo getPagination() {
        return pagination;
    }
}
