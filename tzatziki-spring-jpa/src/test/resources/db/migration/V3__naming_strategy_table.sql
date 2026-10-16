-- table of an entity without @Table, named by the physical naming strategy
create table jedi_master
(
    id        SERIAL PRIMARY KEY,
    full_name VARCHAR(255)
);
