create table users
(
    id         SERIAL PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name  VARCHAR(255) NOT NULL,
    birth_date timestamp with time zone,
    updated_at timestamp with time zone,
    group_id   INT
);

create table superusers
(
    id         SERIAL PRIMARY KEY REFERENCES users(id),
    role       VARCHAR(255) NOT NULL
);

create table groups
(
    id   SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

create table evilness
(
    id   SERIAL PRIMARY KEY,
    evil BOOL
);

CREATE OR REPLACE FUNCTION update_timestamp()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_timestamp_trigger
    BEFORE INSERT OR UPDATE
    ON users
    FOR EACH ROW
EXECUTE PROCEDURE update_timestamp();
