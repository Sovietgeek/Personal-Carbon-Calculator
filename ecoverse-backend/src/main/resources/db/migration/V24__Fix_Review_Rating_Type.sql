-- V24: Fix reviews.rating column type from SMALLINT to INTEGER
-- Hibernate maps Java Integer to INTEGER, but V23 created it as SMALLINT

ALTER TABLE reviews ALTER COLUMN rating TYPE INTEGER USING rating::INTEGER;
