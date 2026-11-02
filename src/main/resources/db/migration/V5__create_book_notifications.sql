CREATE TABLE book_notifications (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,
    book_id BIGINT,
    book_name VARCHAR(255),
    message VARCHAR(512) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_book_notifications_created_at ON book_notifications (created_at DESC);
