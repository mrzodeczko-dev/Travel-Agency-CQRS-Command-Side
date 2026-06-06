create table if not exists shedlock (
    name varchar(255) not null,
    lock_until timestamp not null,
    locked_at timestamp not null default current_timestamp,
    locked_by varchar(255) not null,
    primary key (name)
);