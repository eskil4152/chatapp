CREATE TABLE invites
(
    id UUID PRIMARY KEY,
    type VARCHAR(24) NOT NULL CHECK (type IN ('FRIEND_REQUEST', 'ROOM_INVITE', 'OPEN_ROOM_INVITE')),
    from_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    to_user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    room_id UUID REFERENCES rooms(id) ON DELETE CASCADE,
    usages INT,
    max_usages INT,
    expires_at TIMESTAMP NOT NULL,
    status VARCHAR(12) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'EXHAUSTED')),

    CONSTRAINT chk_usages_non_negative CHECK (usages IS NULL OR usages >= 0),
    CONSTRAINT chk_max_usages_positive CHECK (max_usages IS NULL OR max_usages > 0),
    CONSTRAINT chk_usages_within_limit CHECK (
        usages IS NULL OR max_usages IS NULL OR usages <= max_usages
        ),

    CONSTRAINT chk_friend_request CHECK (
        type != 'FRIEND_REQUEST'
            OR (to_user_id IS NOT NULL AND room_id IS NULL AND usages IS NULL AND max_usages IS NULL)
        ),
    CONSTRAINT chk_room_invite CHECK (
        type != 'ROOM_INVITE'
            OR (to_user_id IS NOT NULL AND room_id IS NOT NULL AND usages IS NULL AND max_usages IS NULL)
        ),
    CONSTRAINT chk_open_room_invite CHECK (
        type != 'OPEN_ROOM_INVITE'
            OR (to_user_id IS NULL AND room_id IS NOT NULL AND usages IS NOT NULL AND max_usages IS NOT NULL)
        )
);