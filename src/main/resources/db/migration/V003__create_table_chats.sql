CREATE TABLE chats (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    message TEXT,
    ciphertext BYTEA,
    nonce BYTEA,
    key_version INT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chats_one_of_plain_or_encrypted CHECK (
        (message IS NOT NULL AND ciphertext IS NULL AND nonce IS NULL AND key_version IS NULL)
            OR
        (message IS NULL AND ciphertext IS NOT NULL AND nonce IS NOT NULL AND key_version IS NOT NULL)
    )
);