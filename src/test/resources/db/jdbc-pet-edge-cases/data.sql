INSERT INTO types (id, name) VALUES (1, 'cat');
INSERT INTO types (id, name) VALUES (2, 'dog');

INSERT INTO owners (id, first_name, last_name, address, city, telephone)
VALUES (1, 'George', 'Franklin', '110 W. Liberty St.', 'Madison', '6085551023');

INSERT INTO pets (id, name, birth_date, type_id, owner_id) VALUES (1, 'Leo', DATE '2010-09-07', 1, 1);
-- pet without owner: owner_id IS NULL
INSERT INTO pets (id, name, birth_date, type_id, owner_id) VALUES (2, 'Stray', DATE '2012-06-08', 2, NULL);

INSERT INTO visits (id, pet_id, visit_date, description) VALUES (1, 2, DATE '2013-01-01', 'rabies shot');

ALTER TABLE pets ALTER COLUMN id RESTART WITH 100;
ALTER TABLE visits ALTER COLUMN id RESTART WITH 100;
