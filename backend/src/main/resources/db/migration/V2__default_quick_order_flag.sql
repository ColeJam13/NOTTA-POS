-- ============================================================================
-- V2: tables.is_quick_order gets a default, and no row is left without a value
-- ============================================================================
-- WHY: The column was added before it had a default. Tables inserted by plain
--      SQL (the seed file never mentions this column) got NULL, and the Floor
--      Map, which asks for "is_quick_order = false", showed none of them. The
--      entity has declared "default false" since #264, but Hibernate only
--      applies a default when it creates a table, never to one that exists.
--      So a brand-new database had the default and an older one did not.
--
-- After this file every database is the same: existing rows that held NULL are
-- regular tables (false), and a row inserted without the column gets false.
--
-- Safe to run on a database that already has the default: both statements
-- then change nothing.
-- ============================================================================

UPDATE tables SET is_quick_order = false WHERE is_quick_order IS NULL;

ALTER TABLE tables ALTER COLUMN is_quick_order SET DEFAULT false;
