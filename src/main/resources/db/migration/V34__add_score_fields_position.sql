-- Pin the order of a scoresheet's criteria. The score_fields collection was an
-- unordered bag (@OneToMany + @JoinColumn, no @OrderColumn), so the database
-- could return the criteria in any order — most scoresheets happened to come
-- back in MJP order, but not all. A `position` column + @OrderColumn makes the
-- order deterministic everywhere (judge views, entrant dialog, PDF).
ALTER TABLE score_fields ADD COLUMN position INTEGER NOT NULL DEFAULT 0;

-- Backfill existing rows by the canonical MJP criterion order.
UPDATE score_fields SET position = CASE field_name
    WHEN 'Appearance'         THEN 0
    WHEN 'Aroma/Bouquet'      THEN 1
    WHEN 'Flavour and Body'   THEN 2
    WHEN 'Finish'             THEN 3
    WHEN 'Overall Impression' THEN 4
    ELSE 0
END;
