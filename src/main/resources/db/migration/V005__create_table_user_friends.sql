CREATE TABLE user_friends
(
    user_id UUID NOT NULL,
    friend_id UUID NOT NULL,
    since TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (user_id, friend_id),
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_friend FOREIGN KEY (friend_id) REFERENCES users(id) ON DELETE CASCADE
)