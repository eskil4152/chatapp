CREATE TABLE rooms
(
    id UUID primary key,
    name VARCHAR(48) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);