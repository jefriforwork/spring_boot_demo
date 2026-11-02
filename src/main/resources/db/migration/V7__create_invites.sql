CREATE TABLE invites (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    role VARCHAR(20) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_invites_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_invites_role CHECK (role IN ('USER', 'STAFF'))
);

CREATE INDEX idx_invites_email ON invites (email);
