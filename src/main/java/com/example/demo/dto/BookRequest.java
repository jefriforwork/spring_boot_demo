package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class BookRequest {

    @NotBlank(message = "name must not be blank")
    @Size(max = 255, message = "name must be at most 255 characters")
    private String name;

    @NotNull(message = "authorId must not be null")
    private Long authorId;

    private List<@NotBlank(message = "tag must not be blank") @Size(max = 255, message = "tag must be at most 255 characters") String> tags;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
