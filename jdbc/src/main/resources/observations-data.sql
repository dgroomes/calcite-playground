insert into observation_types (id, description)
values (1, 'Uninteresting observation'),
       (2, 'Interesting observation');

insert into observations (id, observation, type_id)
values (1, 'The sky is blue', 1),
       (2, 'The speed of light can circle the earth 7 times in a second', 2);
