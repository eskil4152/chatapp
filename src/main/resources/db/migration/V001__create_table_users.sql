CREATE TABLE users
(
    id UUID primary key,
    username VARCHAR(16) NOT NULL,
    password VARCHAR(255) NOT NULL,
    bio VARCHAR(500),
    email VARCHAR(254),
    full_name VARCHAR(100),
    avatar_url VARCHAR(2048),
    birthday DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);