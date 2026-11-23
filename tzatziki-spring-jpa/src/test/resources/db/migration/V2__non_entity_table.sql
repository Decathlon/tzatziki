-- table without JPA entity nor repository (e.g. a join or audit table)
create table audit_logs
(
    id      SERIAL PRIMARY KEY,
    message VARCHAR(255) NOT NULL,
    success BOOL
);
