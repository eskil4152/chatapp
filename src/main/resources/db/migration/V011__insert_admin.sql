INSERT INTO users (id, username, password, role)
    VALUES (
       gen_random_uuid(),
       'admin',
       '${admin.password}',
       'ADMIN'
    );