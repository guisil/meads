CREATE TABLE categories (
    id             UUID         PRIMARY KEY,
    code           VARCHAR(50)  NOT NULL UNIQUE,
    name           VARCHAR(255) NOT NULL,
    description    TEXT         NOT NULL,
    scoring_system VARCHAR(50)  NOT NULL,
    parent_code    VARCHAR(50)  REFERENCES categories(code)
);

-- MJP Category Catalog
-- Source: Mead Judging Programme (meadjudgingprogramme.com)

-- Main categories (parents)
INSERT INTO categories (id, code, name, description, scoring_system) VALUES
    (gen_random_uuid(), 'M1', 'Traditional Mead', 'Mead without any flavoring additions besides honey (and possibly wood)', 'MJP'),
    (gen_random_uuid(), 'M2', 'Fruit Meads (Melomels)', 'Mead with fruit additions', 'MJP'),
    (gen_random_uuid(), 'M3', 'Spiced Meads (Metheglins)', 'Mead with spice, herb, or vegetable additions', 'MJP'),
    (gen_random_uuid(), 'M4', 'Specialty Meads', 'Mead with specialty ingredients or processes', 'MJP');

-- M1: Traditional Mead
INSERT INTO categories (id, code, name, description, scoring_system, parent_code) VALUES
    (gen_random_uuid(), 'M1A', 'Traditional Mead (Dry)', 'Traditional mead, dry', 'MJP', 'M1'),
    (gen_random_uuid(), 'M1B', 'Traditional Mead (Medium)', 'Traditional mead, semi-sweet', 'MJP', 'M1'),
    (gen_random_uuid(), 'M1C', 'Traditional Mead (Sweet)', 'Traditional mead, sweet', 'MJP', 'M1');

-- M2: Fruit Meads (Melomels)
INSERT INTO categories (id, code, name, description, scoring_system, parent_code) VALUES
    (gen_random_uuid(), 'M2A', 'Pome Fruit Melomel', 'Mead with pome fruits — apples, pears, quince', 'MJP', 'M2'),
    (gen_random_uuid(), 'M2B', 'Pyment', 'Mead with grapes', 'MJP', 'M2'),
    (gen_random_uuid(), 'M2C', 'Berry Melomel', 'Mead with berry fruits', 'MJP', 'M2'),
    (gen_random_uuid(), 'M2D', 'Stone Fruit Melomel', 'Mead with stone fruits', 'MJP', 'M2'),
    (gen_random_uuid(), 'M2E', 'Other Fruit Melomel', 'Mead with other fruits or fruit combinations', 'MJP', 'M2');

-- M3: Spiced Meads (Metheglins)
INSERT INTO categories (id, code, name, description, scoring_system, parent_code) VALUES
    (gen_random_uuid(), 'M3A', 'Fruit and Spice Mead', 'Mead with one or more fruits and one or more spices', 'MJP', 'M3'),
    (gen_random_uuid(), 'M3B', 'Metheglin', 'Mead with herbs, spices, or vegetables', 'MJP', 'M3'),
    (gen_random_uuid(), 'M3C', 'Other Metheglin', 'Mead with coffee, chocolate, chili, nuts, or seeds', 'MJP', 'M3');

-- M4: Specialty Meads
INSERT INTO categories (id, code, name, description, scoring_system, parent_code) VALUES
    (gen_random_uuid(), 'M4A', 'Braggot', 'Mead with malt / beer-style honey beverage', 'MJP', 'M4'),
    (gen_random_uuid(), 'M4B', 'Historical Mead', 'Mead made using historical methods or recipes', 'MJP', 'M4'),
    (gen_random_uuid(), 'M4C', 'Experimental Mead', 'Mead with novel ingredients or processes', 'MJP', 'M4'),
    (gen_random_uuid(), 'M4D', 'Honey Alcoholic Beverage', 'Other honey-containing alcoholic beverages — distillates, tinctures, liqueurs', 'MJP', 'M4'),
    (gen_random_uuid(), 'M4E', 'Bochet', 'Mead made with caramelized honey', 'MJP', 'M4'),
    (gen_random_uuid(), 'M4S', 'Session Mead', 'Mead under 7.5% ABV', 'MJP', 'M4');
