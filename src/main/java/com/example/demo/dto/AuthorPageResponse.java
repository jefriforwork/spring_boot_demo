package com.example.demo.dto;

import com.example.demo.entity.Author;
import org.springframework.data.domain.Page;

import java.util.List;

public class AuthorPageResponse {

    private final String dataSource;
    private final List<AuthorResponse> data;
    private final PageInfo pagination;

    public AuthorPageResponse(String dataSource, List<AuthorResponse> data, PageInfo pagination) {
        this.dataSource = dataSource;
        this.data = data;
        this.pagination = pagination;
    }

    public static AuthorPageResponse from(Page<Author> page, String dataSource) {
        List<AuthorResponse> authors = page.getContent().stream().map(AuthorResponse::from).toList();
        return new AuthorPageResponse(dataSource, authors, PageInfo.from(page));
    }

    public String getDataSource() {
        return dataSource;
    }

    public List<AuthorResponse> getData() {
        return data;
    }

    public PageInfo getPagination() {
        return pagination;
    }
}
