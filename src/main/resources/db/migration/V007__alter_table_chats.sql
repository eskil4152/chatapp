ALTER TABLE chats
    ADD COLUMN ciphertext BYTEA,
    ADD COLUMN nonce BYTEA,
    ADD COLUMN key_version INT NOT NULL DEFAULT 1,

    ADD CONSTRAINT chats_one_of_plain_or_encrypted CHECK (
    (message IS NOT NULL AND ciphertext IS NULL AND nonce IS NULL AND key_version IS NULL)
    OR
    (message IS NULL AND ciphertext IS NOT NULL AND nonce IS NOT NULL AND key_version IS NOT NULL)
    );