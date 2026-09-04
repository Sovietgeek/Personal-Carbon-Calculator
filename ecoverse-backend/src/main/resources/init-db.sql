-- ================================================================
-- ECOVERSE — Seed Data for PostgreSQL
-- Uses INSERT ... ON CONFLICT for idempotent inserts
-- Runs via Docker init script (docker-entrypoint-initdb.d)
-- ================================================================

-- Emission Factors: Transport
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (1, 'transport', 'car-petrol', 0.21, 'kg/km') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (2, 'transport', 'car-diesel', 0.24, 'kg/km') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (3, 'transport', 'car-ev', 0.053, 'kg/km') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (4, 'transport', 'car-hybrid', 0.12, 'kg/km') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (5, 'transport', 'motorcycle', 0.113, 'kg/km') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (6, 'transport', 'bus', 0.089, 'kg/km') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (7, 'transport', 'train', 0.041, 'kg/km') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (8, 'transport', 'flight-domestic', 0.255, 'kg/km') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (9, 'transport', 'flight-international', 0.195, 'kg/km') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (10, 'transport', 'bicycle', 0.0, 'kg/km') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (11, 'transport', 'walking', 0.0, 'kg/km') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (12, 'transport', 'rickshaw', 0.073, 'kg/km') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (13, 'transport', 'taxi', 0.184, 'kg/km') ON CONFLICT (category, type) DO NOTHING;

-- Emission Factors: Energy
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (14, 'energy', 'electricity', 0.82, 'kg/kWh') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (15, 'energy', 'natural-gas', 2.0, 'kg/m³') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (16, 'energy', 'lpg', 2.98, 'kg/kg') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (17, 'energy', 'diesel-generator', 0.9, 'kg/kWh') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (18, 'energy', 'solar', -0.05, 'kg/kWh') ON CONFLICT (category, type) DO NOTHING;

-- Emission Factors: Food
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (19, 'food', 'vegan', 0.47, 'kg/meal') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (20, 'food', 'vegetarian', 1.19, 'kg/meal') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (21, 'food', 'poultry', 1.83, 'kg/meal') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (22, 'food', 'pork', 2.43, 'kg/meal') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (23, 'food', 'beef', 6.61, 'kg/meal') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (24, 'food', 'fish', 1.72, 'kg/meal') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (25, 'food', 'dairy', 3.22, 'kg/meal') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (26, 'food', 'processed', 2.48, 'kg/meal') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (27, 'food', 'organic-local', 0.78, 'kg/meal') ON CONFLICT (category, type) DO NOTHING;

-- Emission Factors: Shopping
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (28, 'shopping', 'clothing', 0.0005, 'kg/₹') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (29, 'shopping', 'electronics', 0.0008, 'kg/₹') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (30, 'shopping', 'furniture', 0.0006, 'kg/₹') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (31, 'shopping', 'books', 0.0003, 'kg/₹') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (32, 'shopping', 'beauty', 0.0004, 'kg/₹') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (33, 'shopping', 'sports', 0.0005, 'kg/₹') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (34, 'shopping', 'other', 0.0005, 'kg/₹') ON CONFLICT (category, type) DO NOTHING;

-- Emission Factors: Waste
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (35, 'waste', 'landfill', 2.5, 'kg/kg') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (36, 'waste', 'recycled', -0.2, 'kg/kg') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (37, 'waste', 'composted', -0.1, 'kg/kg') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (38, 'waste', 'incinerated', 1.5, 'kg/kg') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (39, 'waste', 'e-waste', 4.0, 'kg/kg') ON CONFLICT (category, type) DO NOTHING;

-- Emission Factors: Digital
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (40, 'digital', 'streaming-hd', 0.036, 'kg/hr') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (41, 'digital', 'streaming-4k', 0.07, 'kg/hr') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (42, 'digital', 'video-call', 0.04, 'kg/hr') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (43, 'digital', 'cloud-storage', 0.005, 'kg/GB/month') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (44, 'digital', 'crypto-transaction', 25.0, 'kg/txn') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (45, 'digital', 'email', 0.001, 'kg/100') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (46, 'digital', 'web-browsing', 0.02, 'kg/hr') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (47, 'digital', 'gaming-online', 0.05, 'kg/hr') ON CONFLICT (category, type) DO NOTHING;
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES (48, 'digital', 'ai-query', 0.002, 'kg/query') ON CONFLICT (category, type) DO NOTHING;

-- Sample Eco Products
INSERT INTO products (id, seller_id, name, description, category, price, image_url, eco_rating, is_secondhand, is_available, created_at, updated_at) VALUES
(1, 1, 'Bamboo Toothbrush Set', '100% biodegradable bamboo toothbrush set of 4. Zero plastic packaging.', 'beauty', 299.0, NULL, 5, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING;

INSERT INTO products (id, seller_id, name, description, category, price, image_url, eco_rating, is_secondhand, is_available, created_at, updated_at) VALUES
(2, 1, 'Organic Cotton Tote Bag', 'Reusable shopping bag made from 100% organic cotton. Replaces 700+ plastic bags.', 'clothing', 449.0, NULL, 5, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING;

INSERT INTO products (id, seller_id, name, description, category, price, image_url, eco_rating, is_secondhand, is_available, created_at, updated_at) VALUES
(3, 1, 'Solar Power Bank 20000mAh', 'High-efficiency solar charging power bank. Charges phones 4-5 times.', 'electronics', 2499.0, NULL, 4, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING;

INSERT INTO products (id, seller_id, name, description, category, price, image_url, eco_rating, is_secondhand, is_available, created_at, updated_at) VALUES
(4, 1, 'Recycled Notebook Set', 'Set of 3 notebooks made from 100% recycled paper with soy-based ink.', 'books', 399.0, NULL, 5, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING;

INSERT INTO products (id, seller_id, name, description, category, price, image_url, eco_rating, is_secondhand, is_available, created_at, updated_at) VALUES
(5, 1, 'Stainless Steel Water Bottle', '750ml double-wall insulated bottle. BPA-free, keeps drinks cold 24hr.', 'sports', 799.0, NULL, 5, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING;

INSERT INTO products (id, seller_id, name, description, category, price, image_url, eco_rating, is_secondhand, is_available, created_at, updated_at) VALUES
(6, 1, 'Beeswax Food Wraps', 'Set of 3 reusable beeswax wraps. Replace plastic cling film.', 'beauty', 549.0, NULL, 5, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING;

INSERT INTO products (id, seller_id, name, description, category, price, image_url, eco_rating, is_secondhand, is_available, created_at, updated_at) VALUES
(7, 1, 'Pre-owned Yoga Mat', 'Gently used premium eco-friendly yoga mat. 80% less manufacturing emissions.', 'sports', 699.0, NULL, 4, TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING;

INSERT INTO products (id, seller_id, name, description, category, price, image_url, eco_rating, is_secondhand, is_available, created_at, updated_at) VALUES
(8, 1, 'LED Smart Bulb Pack', 'Pack of 4 WiFi-enabled LED bulbs. Uses 75% less energy than incandescent.', 'electronics', 1899.0, NULL, 4, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING;

INSERT INTO products (id, seller_id, name, description, category, price, image_url, eco_rating, is_secondhand, is_available, created_at, updated_at) VALUES
(9, 1, 'Organic Skincare Kit', 'Complete skincare routine with organic, cruelty-free products.', 'beauty', 1599.0, NULL, 5, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING;

INSERT INTO products (id, seller_id, name, description, category, price, image_url, eco_rating, is_secondhand, is_available, created_at, updated_at) VALUES
(10, 1, 'Compost Bin', 'Indoor composting bin with carbon filter. No smell, easy to use.', 'furniture', 1299.0, NULL, 5, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING;

-- Reset the auto-increment sequence to avoid conflicts with future inserts
SELECT setval('emission_factors_id_seq', (SELECT MAX(id) FROM emission_factors));
SELECT setval('products_id_seq', (SELECT MAX(id) FROM products));
