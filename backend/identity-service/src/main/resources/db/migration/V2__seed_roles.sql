-- V2__seed_roles.sql
-- Chen 2 role mac dinh: USER va ADMIN

INSERT INTO roles (id, name) VALUES
    (gen_random_uuid(), 'USER'),
    (gen_random_uuid(), 'ADMIN')
ON CONFLICT (name) DO NOTHING;
