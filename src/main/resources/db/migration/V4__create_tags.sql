CREATE TABLE tags (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE book_tags (
    book_id BIGINT NOT NULL REFERENCES books (id) ON DELETE CASCADE,
    tag_id BIGINT NOT NULL REFERENCES tags (id),
    PRIMARY KEY (book_id, tag_id)
);

CREATE TABLE author_tags (
    author_id BIGINT NOT NULL REFERENCES authors (id) ON DELETE CASCADE,
    tag_id BIGINT NOT NULL REFERENCES tags (id),
    PRIMARY KEY (author_id, tag_id)
);

CREATE INDEX idx_book_tags_tag_id ON book_tags (tag_id);
CREATE INDEX idx_author_tags_tag_id ON author_tags (tag_id);
