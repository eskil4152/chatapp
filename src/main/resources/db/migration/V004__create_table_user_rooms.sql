CREATE TABLE user_rooms
(
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, room_id),
    role VARCHAR(16) NOT NULL
    CHECK (role IN ('OWNER', 'MEMBER'))
);