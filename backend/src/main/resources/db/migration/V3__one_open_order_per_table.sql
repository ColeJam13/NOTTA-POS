-- V3: two terminals, one check.
--
-- Two changes to the orders table, both so the DATABASE enforces what used to be
-- only a rule in the application's code:
--
--   1. A version number on every order.
--   2. A rule that a table has at most one open order.
--
-- Rules in application code read first and write second ("is there an open order?
-- no -> create one"). Two terminals doing that at the same instant both read "no"
-- and both create one. A rule in the database cannot be raced past.


-- ---------------------------------------------------------------------------
-- 1. The version number (optimistic locking)
-- ---------------------------------------------------------------------------
-- Every save of an order says "I started from version N" and moves it to N + 1.
-- If another terminal saved in between, the row is no longer at N, the save
-- changes nothing, and the application reports a conflict instead of silently
-- overwriting the other terminal's change. Hibernate does this by itself for the
-- field marked @Version on the Order entity.
--
-- Existing orders start at 0. NOT NULL with a DEFAULT, so rows inserted by plain
-- SQL (the seed file, a hand-written fix) get a starting version too.
ALTER TABLE orders ADD COLUMN version bigint NOT NULL DEFAULT 0;


-- ---------------------------------------------------------------------------
-- 2. Tidy up: a table that already has more than one open order
-- ---------------------------------------------------------------------------
-- Older versions of the app could leave a table with two open orders. The rule
-- in step 3 cannot be created while any table breaks it (the migration would
-- fail and the backend would not start), so those tables are put right first.
--
-- For each such table the OLDEST open order is kept, and the items on the newer
-- ones are moved onto it. Nothing a guest ordered is lost: the items, their
-- modifiers (which hang off the item, not the order), their notes, and their
-- kitchen status all stay as they are. Only which order they belong to changes.
--
-- On a database with no such table - the normal case - these statements change
-- no rows.

-- Move the items of every newer open order onto the oldest open order of its table
UPDATE order_items
SET order_id = keeper.keep_order_id
FROM orders AS extra
JOIN (
    SELECT table_id, min(order_id) AS keep_order_id
    FROM orders
    WHERE status = 'open'
    GROUP BY table_id
    HAVING count(*) > 1
) AS keeper ON keeper.table_id = extra.table_id
WHERE order_items.order_id = extra.order_id
  AND extra.status = 'open'
  AND extra.order_id <> keeper.keep_order_id;

-- The same for payments. An open order should not have one (a payment closes the
-- order), but if one exists it must follow the items, not be orphaned.
UPDATE payments
SET order_id = keeper.keep_order_id
FROM orders AS extra
JOIN (
    SELECT table_id, min(order_id) AS keep_order_id
    FROM orders
    WHERE status = 'open'
    GROUP BY table_id
    HAVING count(*) > 1
) AS keeper ON keeper.table_id = extra.table_id
WHERE payments.order_id = extra.order_id
  AND extra.status = 'open'
  AND extra.order_id <> keeper.keep_order_id;

-- The newer open orders are now empty shells. Remove them, the same way the app
-- clears an empty open order off a table. Closed orders are never touched.
DELETE FROM orders AS extra
USING (
    SELECT table_id, min(order_id) AS keep_order_id
    FROM orders
    WHERE status = 'open'
    GROUP BY table_id
    HAVING count(*) > 1
) AS keeper
WHERE extra.table_id = keeper.table_id
  AND extra.status = 'open'
  AND extra.order_id <> keeper.keep_order_id;


-- ---------------------------------------------------------------------------
-- 3. The rule: one open order per table
-- ---------------------------------------------------------------------------
-- A UNIQUE index normally says "no two rows may have the same table_id". The
-- WHERE clause makes it a PARTIAL index: it only covers rows whose status is
-- 'open'. So a table can have any number of closed orders (it serves many
-- parties in a day) but only one open one. A second open order on the same
-- table is refused by PostgreSQL itself, whoever asks and however they ask.
--
-- Hibernate cannot create this kind of index, which is why Flyway was adopted
-- in V1/V2 before this change.
CREATE UNIQUE INDEX orders_one_open_per_table ON orders (table_id) WHERE status = 'open';
