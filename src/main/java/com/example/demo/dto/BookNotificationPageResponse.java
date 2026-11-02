package com.example.demo.dto;

import com.example.demo.entity.BookNotification;
import org.springframework.data.domain.Page;

import java.util.List;

public class BookNotificationPageResponse {

    private final String dataSource;
    private final List<BookNotificationResponse> data;
    private final PageInfo pagination;

    public BookNotificationPageResponse(
            String dataSource,
            List<BookNotificationResponse> data,
            PageInfo pagination) {
        this.dataSource = dataSource;
        this.data = data;
        this.pagination = pagination;
    }

    public static BookNotificationPageResponse from(Page<BookNotification> page, String dataSource) {
        List<BookNotificationResponse> notifications = page.getContent().stream()
                .map(BookNotificationResponse::from)
                .toList();
        return new BookNotificationPageResponse(dataSource, notifications, PageInfo.from(page));
    }

    public String getDataSource() {
        return dataSource;
    }

    public List<BookNotificationResponse> getData() {
        return data;
    }

    public PageInfo getPagination() {
        return pagination;
    }
}
