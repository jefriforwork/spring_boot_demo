CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'STAFF'))
);

-- staff / staffpass, reader / readerpass
INSERT INTO users (username, password, role) VALUES
    ('staff', '$2a$10$NZJq.x31.QrVaP//1Q0P2u0TMZ516jw.QXIQqzLYNQbrZNTjVc8uK', 'STAFF'),
    ('reader', '$2a$10$tbhA/.LbGWZUGqcQego0/u7659MxTIhRfq9eZKZv3JquCfBSt8zCe', 'USER');
