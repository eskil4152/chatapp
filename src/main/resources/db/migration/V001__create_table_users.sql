CREATE TABLE users
(
    id UUID primary key,
    username TEXT NOT NULL,
    password TEXT NOT NULL,
    bio TEXT,
    email TEXT,
    full_name TEXT,
    avatar_url TEXT,
    birthday DATE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);