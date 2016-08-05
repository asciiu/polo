#
# --- !Ups

DROP TABLE IF EXISTS message;
DROP TABLE IF EXISTS account;
DROP TYPE IF EXISTS account_role;

CREATE TYPE account_role AS ENUM (
    'normal',
    'admin'
);

CREATE TABLE account (
    id serial PRIMARY KEY,
    name text NOT NULL,
    email text UNIQUE NOT NULL,
    password text NOT NULL,
    role account_role NOT NULL,
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
INSERT INTO account (name, email, role, password) values ('Admin User', 'admin@email.com', 'admin', '$2a$10$8K1p/a0dL1LXMIgoEDFrwOfMQbLgtnOoKsWc.6U6H0llP3puzeeEu');
INSERT INTO account (name, email, role, password) values ('Bob Minion', 'bob@email.com', 'normal', '$2a$10$8K1p/a0dL1LXMIgoEDFrwOfMQbLgtnOoKsWc.6U6H0llP3puzeeEu');
INSERT INTO message (content, tag_list) values ('Welcome to the templatesite!', '{"welcome", "first message", "english"}');


# --- !Downs

DROP TABLE message;
DROP TABLE account;
DROP TABLE account_role;