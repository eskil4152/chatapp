CREATE TABLE rooms
(
    id UUID primary key,
    name TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);