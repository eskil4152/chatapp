CREATE TABLE invites
(
    id UUID PRIMARY KEY,
    type VARCHAR(24) CHECK ( type IN ('FRIEND_REQUEST', 'ROOM_INVITE', 'OPEN_ROOM_INVITE') ),
    from_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    to_user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    room_id UUID REFERENCES rooms(id) ON DELETE CASCADE,
    usages INT,
    max_usages INT,
    expires_at TIMESTAMP NOT NULL,
    status VARCHAR(12) NOT NULL CHECK ( status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'EXHAUSTED') )
);