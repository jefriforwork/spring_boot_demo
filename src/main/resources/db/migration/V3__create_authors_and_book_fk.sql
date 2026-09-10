CREATE TABLE authors (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    country VARCHAR(255) NOT NULL
);

INSERT INTO authors (name, country)
SELECT DISTINCT author, 'UNKNOWN'
FROM books;

ALTER TABLE books ADD COLUMN author_id BIGINT;

UPDATE books b
SET author_id = a.id
FROM authors a
WHERE a.name = b.author;

ALTER TABLE books ALTER COLUMN author_id SET NOT NULL;

ALTER TABLE books
    ADD CONSTRAINT fk_books_author
    FOREIGN KEY (author_id) REFERENCES authors (id);

CREATE INDEX idx_books_author_id ON books (author_id);
CREATE INDEX idx_authors_country ON authors (country);

ALTER TABLE books DROP COLUMN author;
