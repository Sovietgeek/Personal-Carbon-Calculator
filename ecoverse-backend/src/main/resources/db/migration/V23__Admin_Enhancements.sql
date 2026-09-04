-- V23: Admin Control Center enhancements
-- Adds: reviews table, ai_usage_logs table, additional indexes

-- ================================================================
-- REVIEWS TABLE
-- ================================================================
CREATE TABLE reviews (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id      BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    rating          SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title           VARCHAR(255),
    comment         TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'HIDDEN', 'FLAGGED')),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reviews_product_status ON reviews(product_id, status);
CREATE INDEX idx_reviews_user_created ON reviews(user_id, created_at);

-- ================================================================
-- AI USAGE LOGS TABLE
-- ================================================================
CREATE TABLE ai_usage_logs (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT REFERENCES users(id) ON DELETE SET NULL,
    provider        VARCHAR(50) NOT NULL,
    model           VARCHAR(100),
    input_tokens    INTEGER,
    output_tokens   INTEGER,
    success         BOOLEAN NOT NULL DEFAULT TRUE,
    error_message   VARCHAR(500),
    latency_ms      INTEGER,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_usage_user_created ON ai_usage_logs(user_id, created_at);
CREATE INDEX idx_ai_usage_provider ON ai_usage_logs(provider);

-- ================================================================
-- ADDITIONAL INDEXES FOR ADMIN QUERIES
-- ================================================================
CREATE INDEX idx_audit_logs_action_created ON audit_logs(action, created_at);
CREATE INDEX idx_users_enabled_role ON users(enabled, role);
CREATE INDEX idx_users_created_at ON users(created_at);

-- ================================================================
-- SEED SAMPLE REVIEWS
-- ================================================================
INSERT INTO reviews (user_id, product_id, rating, title, comment, status, created_at, updated_at) VALUES
(1, 11, 5, 'Excellent cutlery set', 'Love the bamboo quality. Replaced all our plastic utensils with this set.', 'APPROVED', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),
(1, 13, 4, 'Great meal prep containers', 'Glass feels premium. The lids seal well. Only wish they were slightly larger.', 'APPROVED', NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days'),
(1, 14, 5, 'Beautiful coconut bowls', 'These look amazing on the dining table. Each one is unique.', 'APPROVED', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),
(1, 51, 4, 'Good bamboo toothbrush', 'Soft bristles, comfortable grip. The pack of 4 lasts a good while.', 'APPROVED', NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days'),
(1, 86, 3, 'Decent organic tee', 'Fabric is soft but sizing runs a bit small. Would order a size up.', 'APPROVED', NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days'),
(1, 121, 5, 'Best dish soap', 'Cuts through grease easily and smells wonderful. No harsh chemicals.', 'APPROVED', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
(1, 206, 4, 'Reliable solar power bank', 'Charges well in direct sunlight. Good capacity for phone charging.', 'APPROVED', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
(1, 311, 5, 'Must-read book', 'Practical tips for reducing waste. Changed how our family shops.', 'APPROVED', NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days'),
(1, 331, 5, 'Meaningful gift', 'Gave the tree certificate to my mom. She loved the idea.', 'APPROVED', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),
(1, 12, 3, 'Lunch box is okay', 'Stainless steel is durable but the lid could be tighter. Leaks with liquids.', 'PENDING', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
(1, 20, 2, 'Clay pot arrived cracked', 'The pot itself is beautiful but it arrived with a small crack. Needs better packaging.', 'PENDING', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
(1, 231, 4, 'Good solar panel', 'Output is as advertised. Installation was straightforward.', 'APPROVED', NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days'),
(1, 251, 5, 'Stylish water bottle', 'Keeps water cold all day. Love the copper finish.', 'APPROVED', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),
(1, 271, 4, 'Nice bike accessories', 'Good quality for the price. The light is particularly bright.', 'APPROVED', NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days'),
(1, 151, 3, 'Zero waste kit is basic', 'Good starter kit but the items feel a bit cheap. Expected better quality at this price.', 'FLAGGED', NOW() - INTERVAL '9 days', NOW() - INTERVAL '9 days');

-- ================================================================
-- SEED SAMPLE AI USAGE LOGS
-- ================================================================
INSERT INTO ai_usage_logs (user_id, provider, model, input_tokens, output_tokens, success, error_message, latency_ms, created_at) VALUES
(1, 'gemini', 'gemini-3.5-flash-lite', 245, 380, TRUE, NULL, 1200, NOW() - INTERVAL '1 hour'),
(1, 'gemini', 'gemini-3.5-flash-lite', 180, 290, TRUE, NULL, 980, NOW() - INTERVAL '2 hours'),
(1, 'gemini', 'gemini-3.5-flash-lite', 320, 410, TRUE, NULL, 1500, NOW() - INTERVAL '3 hours'),
(1, 'gemini', 'gemini-3.5-flash-lite', 150, 0, FALSE, 'Model temporarily unavailable', 5000, NOW() - INTERVAL '5 hours'),
(1, 'gemini', 'gemini-3.5-flash-lite', 210, 350, TRUE, NULL, 1100, NOW() - INTERVAL '6 hours'),
(1, 'gemini', 'gemini-3.5-flash-lite', 275, 420, TRUE, NULL, 1350, NOW() - INTERVAL '8 hours'),
(1, 'gemini', 'gemini-3.5-flash-lite', 190, 310, TRUE, NULL, 1050, NOW() - INTERVAL '10 hours'),
(1, 'gemini', 'gemini-3.5-flash-lite', 400, 0, FALSE, 'Rate limit exceeded', 200, NOW() - INTERVAL '12 hours'),
(1, 'gemini', 'gemini-3.5-flash-lite', 230, 370, TRUE, NULL, 1280, NOW() - INTERVAL '1 day'),
(1, 'gemini', 'gemini-3.5-flash-lite', 165, 260, TRUE, NULL, 920, NOW() - INTERVAL '1 day');

-- ================================================================
-- UPDATE EXISTING PRODUCTS WITH RATING DATA FROM REVIEWS
-- ================================================================
UPDATE products SET rating = 4.5, rating_count = 1 WHERE id = 11;
UPDATE products SET rating = 4.0, rating_count = 1 WHERE id = 13;
UPDATE products SET rating = 5.0, rating_count = 1 WHERE id = 14;
UPDATE products SET rating = 4.0, rating_count = 1 WHERE id = 51;
UPDATE products SET rating = 3.0, rating_count = 1 WHERE id = 86;
UPDATE products SET rating = 5.0, rating_count = 1 WHERE id = 121;
UPDATE products SET rating = 4.0, rating_count = 1 WHERE id = 206;
UPDATE products SET rating = 5.0, rating_count = 1 WHERE id = 311;
UPDATE products SET rating = 5.0, rating_count = 1 WHERE id = 331;
UPDATE products SET rating = 3.0, rating_count = 1 WHERE id = 12;
UPDATE products SET rating = 2.0, rating_count = 1 WHERE id = 20;
UPDATE products SET rating = 4.0, rating_count = 1 WHERE id = 231;
UPDATE products SET rating = 5.0, rating_count = 1 WHERE id = 251;
UPDATE products SET rating = 4.0, rating_count = 1 WHERE id = 271;
