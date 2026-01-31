-- Spring Modulith 2.0 event publication table fix
-- This runs AFTER Hibernate creates tables (with defer-datasource-initialization=true)
-- We alter the serialized_event column to TEXT type to support large event payloads

ALTER TABLE event_publication ALTER COLUMN serialized_event TYPE TEXT;
