CREATE TABLE rooms
(
    id UUID primary key,
    name VARCHAR(48) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    encrypted BOOLEAN NOT NULL DEFAULT FALSE,
    key_version INT
);