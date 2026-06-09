-- Cross-division physical-table sharing within a competition.
-- When TRUE, busy-check at startRound spans all divisions of the competition,
-- matching by physical-table label (so two divisions can't run rounds at the
-- same physical "Table 1" concurrently). When FALSE, busy-check stays per
-- division — Division A's Table 1 and Division B's Table 1 are independent.
-- TRUE is the new default; existing competitions are also migrated to TRUE.
ALTER TABLE competitions
    ADD COLUMN shared_tables BOOLEAN NOT NULL DEFAULT TRUE;
