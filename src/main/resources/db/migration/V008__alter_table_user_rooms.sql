CREATE INDEX IF NOT EXISTS ix_user_rooms_user_id ON user_rooms(user_id);
CREATE INDEX IF NOT EXISTS ix_user_rooms_room_id ON user_rooms(room_id);