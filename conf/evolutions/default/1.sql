#
# --- !Ups

DROP TABLE IF EXISTS message;
DROP TABLE IF EXISTS users;
DROP TYPE IF EXISTS user_role;

CREATE TYPE user_role AS ENUM (
    'normal',
    'admin'
);

CREATE TABLE users (
    id serial PRIMARY KEY,
    name text NOT NULL,
    email text UNIQUE NOT NULL,
    email_confirmed BOOLEAN NOT NULL,
    password text NOT NULL,
    role user_role NOT NULL,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

CREATE TABLE message (
    id serial PRIMARY KEY,
    content text NOT NULL,
    tag_list text[] NOT NULL,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO player;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO player;

-- bcrypted password values are password in both users
INSERT INTO users (name, email, email_confirmed, role, password) values ('Admin User', 'admin@email.com', true, 'admin', '$2a$10$8K1p/a0dL1LXMIgoEDFrwOfMQbLgtnOoKsWc.6U6H0llP3puzeeEu');
INSERT INTO users (name, email, email_confirmed, role, password) values ('Bob Minion', 'bob@email.com', true, 'normal', '$2a$10$8K1p/a0dL1LXMIgoEDFrwOfMQbLgtnOoKsWc.6U6H0llP3puzeeEu');
INSERT INTO message (content, tag_list) values ('Welcome to the templatesite!', '{"welcome", "first message", "english"}');


# --- !Downs

DROP TABLE message;
DROP TABLE users;
DROP TABLE user_role;