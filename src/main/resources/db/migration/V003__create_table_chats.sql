CREATE TABLE chats (
    id UUID PRIMARY KEY,
    room_id UUID references rooms(id),
    user_id UUID references users(id),
    message TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)