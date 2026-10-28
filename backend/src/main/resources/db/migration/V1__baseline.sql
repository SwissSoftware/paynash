-- Baseline migration. Schema objects are added by later versions (V2, V3, ...).
-- Flyway is forward-only here; rollback means restoring a backup (see design.md, Migration Plan).
SELECT 1;
