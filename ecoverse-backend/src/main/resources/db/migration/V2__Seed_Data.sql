-- ================================================================
-- ECOVERSE — V2: Seed Data (Emission Factors + Sample Products)
-- PostgreSQL compatible: INSERT ON CONFLICT DO NOTHING
-- ================================================================

-- Emission Factors: Transport
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES
(1, 'transport', 'car-petrol', 0.21, 'kg/km'),
(2, 'transport', 'car-diesel', 0.24, 'kg/km'),
(3, 'transport', 'car-ev', 0.053, 'kg/km'),
(4, 'transport', 'car-hybrid', 0.12, 'kg/km'),
(5, 'transport', 'motorcycle', 0.113, 'kg/km'),
(6, 'transport', 'bus', 0.089, 'kg/km'),
(7, 'transport', 'train', 0.041, 'kg/km'),
(8, 'transport', 'flight-domestic', 0.255, 'kg/km'),
(9, 'transport', 'flight-international', 0.195, 'kg/km'),
(10, 'transport', 'bicycle', 0.0, 'kg/km'),
(11, 'transport', 'walking', 0.0, 'kg/km'),
(12, 'transport', 'rickshaw', 0.073, 'kg/km'),
(13, 'transport', 'taxi', 0.184, 'kg/km')
ON CONFLICT (category, type) DO NOTHING;

-- Emission Factors: Energy
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES
(14, 'energy', 'electricity', 0.82, 'kg/kWh'),
(15, 'energy', 'natural-gas', 2.0, 'kg/m³'),
(16, 'energy', 'lpg', 2.98, 'kg/kg'),
(17, 'energy', 'diesel-generator', 0.9, 'kg/kWh'),
(18, 'energy', 'solar', -0.05, 'kg/kWh')
ON CONFLICT (category, type) DO NOTHING;

-- Emission Factors: Food
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES
(19, 'food', 'vegan', 0.47, 'kg/meal'),
(20, 'food', 'vegetarian', 1.19, 'kg/meal'),
(21, 'food', 'poultry', 1.83, 'kg/meal'),
(22, 'food', 'pork', 2.43, 'kg/meal'),
(23, 'food', 'beef', 6.61, 'kg/meal'),
(24, 'food', 'fish', 1.72, 'kg/meal'),
(25, 'food', 'dairy', 3.22, 'kg/meal'),
(26, 'food', 'processed', 2.48, 'kg/meal'),
(27, 'food', 'organic-local', 0.78, 'kg/meal')
ON CONFLICT (category, type) DO NOTHING;

-- Emission Factors: Shopping
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES
(28, 'shopping', 'clothing', 0.0005, 'kg/₹'),
(29, 'shopping', 'electronics', 0.0008, 'kg/₹'),
(30, 'shopping', 'furniture', 0.0006, 'kg/₹'),
(31, 'shopping', 'books', 0.0003, 'kg/₹'),
(32, 'shopping', 'beauty', 0.0004, 'kg/₹'),
(33, 'shopping', 'sports', 0.0005, 'kg/₹'),
(34, 'shopping', 'other', 0.0005, 'kg/₹')
ON CONFLICT (category, type) DO NOTHING;

-- Emission Factors: Waste
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES
(35, 'waste', 'landfill', 2.5, 'kg/kg'),
(36, 'waste', 'recycled', -0.2, 'kg/kg'),
(37, 'waste', 'composted', -0.1, 'kg/kg'),
(38, 'waste', 'incinerated', 1.5, 'kg/kg'),
(39, 'waste', 'e-waste', 4.0, 'kg/kg')
ON CONFLICT (category, type) DO NOTHING;

-- Emission Factors: Digital
INSERT INTO emission_factors (id, category, type, factor, unit) VALUES
(40, 'digital', 'streaming-hd', 0.036, 'kg/hr'),
(41, 'digital', 'streaming-4k', 0.07, 'kg/hr'),
(42, 'digital', 'video-call', 0.04, 'kg/hr'),
(43, 'digital', 'cloud-storage', 0.005, 'kg/GB/month'),
(44, 'digital', 'crypto-transaction', 25.0, 'kg/txn'),
(45, 'digital', 'email', 0.001, 'kg/100'),
(46, 'digital', 'web-browsing', 0.02, 'kg/hr'),
(47, 'digital', 'gaming-online', 0.05, 'kg/hr'),
(48, 'digital', 'ai-query', 0.002, 'kg/query')
ON CONFLICT (category, type) DO NOTHING;

-- Reset the ID sequence after manual inserts
SELECT setval('emission_factors_id_seq', (SELECT MAX(id) FROM emission_factors));

-- Seed demo seller user (id=1) before products reference seller_id=1
-- Password = BCrypt hash of 'DemoSeller@123'
INSERT INTO users (id, name, email, password, country, is_premium, joined_date, created_at, updated_at)
VALUES (1, 'EcoVerse Demo Seller', 'seller@ecoverse.app',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'India', FALSE, NOW(), NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
SELECT setval('users_id_seq', GREATEST((SELECT MAX(id) FROM users), (SELECT last_value FROM users_id_seq)));

-- Sample Eco Products
INSERT INTO products (id, seller_id, name, description, category, price, image_url, eco_rating, is_secondhand, is_available, created_at, updated_at) VALUES
(1, 1, 'Bamboo Toothbrush Set', '100% biodegradable bamboo toothbrush set of 4. Zero plastic packaging.', 'beauty', 299.0, NULL, 5, FALSE, TRUE, NOW(), NOW()),
(2, 1, 'Organic Cotton Tote Bag', 'Reusable shopping bag made from 100% organic cotton. Replaces 700+ plastic bags.', 'clothing', 449.0, NULL, 5, FALSE, TRUE, NOW(), NOW()),
(3, 1, 'Solar Power Bank 20000mAh', 'High-efficiency solar charging power bank. Charges phones 4-5 times.', 'electronics', 2499.0, NULL, 4, FALSE, TRUE, NOW(), NOW()),
(4, 1, 'Recycled Notebook Set', 'Set of 3 notebooks made from 100% recycled paper with soy-based ink.', 'books', 399.0, NULL, 5, FALSE, TRUE, NOW(), NOW()),
(5, 1, 'Stainless Steel Water Bottle', '750ml double-wall insulated bottle. BPA-free, keeps drinks cold 24hr.', 'sports', 799.0, NULL, 5, FALSE, TRUE, NOW(), NOW()),
(6, 1, 'Beeswax Food Wraps', 'Set of 3 reusable beeswax wraps. Replace plastic cling film.', 'beauty', 549.0, NULL, 5, FALSE, TRUE, NOW(), NOW()),
(7, 1, 'Pre-owned Yoga Mat', 'Gently used premium eco-friendly yoga mat. 80% less manufacturing emissions.', 'sports', 699.0, NULL, 4, TRUE, TRUE, NOW(), NOW()),
(8, 1, 'LED Smart Bulb Pack', 'Pack of 4 WiFi-enabled LED bulbs. Uses 75% less energy than incandescent.', 'electronics', 1899.0, NULL, 4, FALSE, TRUE, NOW(), NOW()),
(9, 1, 'Organic Skincare Kit', 'Complete skincare routine with organic, cruelty-free products.', 'beauty', 1599.0, NULL, 5, FALSE, TRUE, NOW(), NOW()),
(10, 1, 'Compost Bin', 'Indoor composting bin with carbon filter. No smell, easy to use.', 'furniture', 1299.0, NULL, 5, FALSE, TRUE, NOW(), NOW())
ON CONFLICT DO NOTHING;

-- Reset the ID sequence after manual inserts
SELECT setval('products_id_seq', (SELECT MAX(id) FROM products));
