CREATE TABLE rooms
(
    id UUID primary key,
    name VARCHAR(48) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    encrypted BOOLEAN NOT NULL DEFAULT FALSE,
    key_version INT,
    type VARCHAR(16) CHECK (type IN ('GROUP', 'PRIVATE'))
);