CREATE TABLE users
(
    id UUID primary key,
    username TEXT NOT NULL,
    password TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);