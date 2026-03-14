alter table users
    add constraint uk_users_username unique (username);