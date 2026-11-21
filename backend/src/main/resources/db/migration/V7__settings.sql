-- V7: a place for settings a manager chooses while the app is running.
--
-- The first one is the restaurant's TEMPLATE: which look every screen is drawn
-- in (step B4b of the Phase 2 plan). The frontend holds the templates
-- themselves; this table holds only the name of the one in use, so that every
-- terminal and phone shows the same thing.
--
-- One row per setting, found by its name:
--
--     setting_key | setting_value
--     ------------+--------------
--     template    | 8-bit
--
-- WHY NAME AND VALUE, and not a column per setting: the next setting is then a
-- new row, written by the app, and not another migration.
--
-- WHY THE TAX RATE IS NOT HERE: notta.tax-rate lives in application.properties
-- and is read when the backend starts. Changing what guests are charged is not
-- something to do from a screen in the middle of a shift.
--
-- The table starts EMPTY. With no row for a setting the app uses that
-- setting's default (for the template, "8-bit": the look it has always had),
-- so an existing restaurant's screens do not change when this migration runs.
--
-- updated_by_* records who last changed a setting. Like the other tables,
-- the staff ID is a plain column with no foreign key, and the name is a copy,
-- so the record still reads correctly after that person has left.
--
-- updated_at is timestamptz (a moment, the same in every time zone), as every
-- table added since V4 uses.

CREATE TABLE settings (
    setting_key         varchar(50)  PRIMARY KEY,
    setting_value       varchar(200) NOT NULL,
    updated_at          timestamptz  NOT NULL,
    updated_by_staff_id bigint,
    updated_by_name     varchar(50)
);
