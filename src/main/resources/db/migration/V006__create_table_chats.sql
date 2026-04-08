CREATE TABLE chats (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    user_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000' REFERENCES users(id) ON DELETE SET DEFAULT,
    message TEXT,
    ciphertext BYTEA,
    nonce BYTEA,
    key_version INT,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')

    CONSTRAINT chats_one_of_plain_or_encrypted CHECK (
        (message IS NOT NULL AND ciphertext IS NULL AND nonce IS NULL AND key_version IS NULL)
            OR
        (message IS NULL AND ciphertext IS NOT NULL AND nonce IS NOT NULL AND key_version IS NOT NULL)
    )
);