-- V5: a staff member can be marked "must choose a new PIN".
--
-- A new database gets one manager with the PIN 1234 so that somebody can sign
-- in. That PIN is the same everywhere and is written in this repository. With
-- this flag set, the backend lets that person do nothing but choose a new PIN.
--
-- NOT NULL with a DEFAULT, so every existing staff member, and any row added by
-- plain SQL, starts as "no change needed". A database whose manager still holds
-- 1234 is found when the backend starts (StaffService.requireChangeOfStarterPin):
-- SQL cannot tell, because only a hash of each PIN is stored.

ALTER TABLE staff ADD COLUMN must_change_pin boolean NOT NULL DEFAULT false;
