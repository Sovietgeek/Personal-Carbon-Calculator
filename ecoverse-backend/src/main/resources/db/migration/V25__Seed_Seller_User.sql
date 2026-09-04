-- V25: Ensure demo seller user exists for product FK constraints.
-- On a fresh DB, V2 already seeds this user, so ON CONFLICT skips it.
-- On an existing DB where V2 was applied before the user seed was added,
-- this migration ensures the seller user exists before V20/V23 try to reference it.
-- Note: If this runs AFTER V20, V20 may still fail on existing databases.
-- For existing databases, delete and recreate the Render database for a clean start.

INSERT INTO users (id, name, email, password, country, is_premium, joined_date, created_at, updated_at)
VALUES (1, 'EcoVerse Demo Seller', 'seller@ecoverse.app',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'India', FALSE, NOW(), NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Reset the ID sequence after manual insert
SELECT setval('users_id_seq', GREATEST((SELECT MAX(id) FROM users), (SELECT last_value FROM users_id_seq)));
