CREATE SCHEMA library;
create table library.books
(
    id    SERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL
);

CREATE SCHEMA store;
create table store.products
(
    id   SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);