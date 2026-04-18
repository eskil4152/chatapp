UPDATE users
SET
    username = 'SUPERUSER',
    bio = 'There can only be one.',
    role = 'SUPERUSER'
WHERE username = 'admin';