create table observation_types
(
    id          int  not null primary key,
    description text not null
);

create table observations
(
    id          int  not null primary key,
    observation text not null,
    type_id     int  not null references observation_types (id)
);
