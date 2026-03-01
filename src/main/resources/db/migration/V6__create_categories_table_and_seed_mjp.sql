CREATE TABLE categories (
    id             UUID         PRIMARY KEY,
    code           VARCHAR(50)  NOT NULL UNIQUE,
    name           VARCHAR(255) NOT NULL,
    description    TEXT         NOT NULL,
    scoring_system VARCHAR(50)  NOT NULL
);

-- MJP Category Catalog
-- Source: Mead Judging Programme (meadjudgingprogramme.com)

-- M1: Traditional Mead
INSERT INTO categories (id, code, name, description, scoring_system) VALUES
    (gen_random_uuid(), 'M1A', 'Traditional Mead (Dry)', 'Traditional mead, dry style — honey, water, and yeast only', 'MJP'),
    (gen_random_uuid(), 'M1B', 'Traditional Mead (Medium)', 'Traditional mead, semi-sweet/semi-dry style', 'MJP'),
    (gen_random_uuid(), 'M1C', 'Traditional Mead (Sweet)', 'Traditional mead, sweet style', 'MJP'),
    (gen_random_uuid(), 'M1V', 'Traditional Mead (Varietal)', 'Traditional mead made with a single honey variety', 'MJP');

-- M2: Fruit Meads (Melomels)
INSERT INTO categories (id, code, name, description, scoring_system) VALUES
    (gen_random_uuid(), 'M2A', 'Pome Fruit Melomel', 'Mead with pome fruits — apples, pears, quince', 'MJP'),
    (gen_random_uuid(), 'M2B', 'Pyment', 'Mead with grapes', 'MJP'),
    (gen_random_uuid(), 'M2C', 'Berry Melomel', 'Mead with berry fruits', 'MJP'),
    (gen_random_uuid(), 'M2D', 'Stone Fruit Melomel', 'Mead with stone fruits', 'MJP'),
    (gen_random_uuid(), 'M2E', 'Other Fruit Melomel', 'Mead with other fruits or fruit combinations', 'MJP');

-- M3: Spiced Meads (Metheglins)
INSERT INTO categories (id, code, name, description, scoring_system) VALUES
    (gen_random_uuid(), 'M3A', 'Fruit and Spice Mead', 'Mead with one or more fruits and one or more spices', 'MJP'),
    (gen_random_uuid(), 'M3B', 'Metheglin', 'Mead with herbs, spices, or vegetables', 'MJP'),
    (gen_random_uuid(), 'M3C', 'Other Metheglin', 'Mead with coffee, chocolate, chili, nuts, or seeds', 'MJP');

-- M4: Specialty Meads
INSERT INTO categories (id, code, name, description, scoring_system) VALUES
    (gen_random_uuid(), 'M4A', 'Braggot', 'Mead with malt and/or hops — beer-style honey beverage', 'MJP'),
    (gen_random_uuid(), 'M4C', 'Experimental Mead', 'Mead with novel ingredients or processes', 'MJP'),
    (gen_random_uuid(), 'M4D', 'Honey Alcoholic Beverage', 'Other honey-containing alcoholic beverages — distillates, tinctures, liqueurs', 'MJP'),
    (gen_random_uuid(), 'M4E', 'Bochet', 'Mead made with caramelized honey', 'MJP'),
    (gen_random_uuid(), 'M4G', 'Sparkling Mead', 'Sparkling mead, champagne style', 'MJP'),
    (gen_random_uuid(), 'M4P', 'Polish Dessert Mead', 'Polish style dessert mead', 'MJP'),
    (gen_random_uuid(), 'M4S', 'Session Mead', 'Mead under 7.5% ABV', 'MJP'),
    (gen_random_uuid(), 'M4Z', 'Alcohol-Free Mead', 'Alcohol-free and low-alcohol mead, up to 3.5% ABV', 'MJP');
