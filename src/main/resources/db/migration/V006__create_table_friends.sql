CREATE TABLE friends
(
    user_a UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (user_a, user_b),
    CHECK (user_a <> user_b)
);