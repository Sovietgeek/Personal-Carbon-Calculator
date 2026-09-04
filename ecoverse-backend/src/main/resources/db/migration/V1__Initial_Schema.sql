-- ================================================================
-- ECOVERSE — V1: Initial Schema (PostgreSQL)
-- All 11 tables matching current JPA entities
-- ================================================================

-- Users
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    country         VARCHAR(255),
    carbon_budget   DOUBLE PRECISION DEFAULT 4.2,
    is_premium      BOOLEAN DEFAULT TRUE,
    joined_date     TIMESTAMP,
    goals_steps     INTEGER DEFAULT 10000,
    goals_sleep     INTEGER DEFAULT 8,
    goals_water     INTEGER DEFAULT 3,
    goals_calories  INTEGER DEFAULT 2000,
    best_streak     INTEGER DEFAULT 0,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL
);

-- Emission Factors
CREATE TABLE emission_factors (
    id          BIGSERIAL PRIMARY KEY,
    category    VARCHAR(255) NOT NULL,
    type        VARCHAR(255) NOT NULL,
    factor      DOUBLE PRECISION NOT NULL,
    unit        VARCHAR(255) NOT NULL,
    CONSTRAINT uk_emission_factor_category_type UNIQUE (category, type)
);

-- Carbon Entries
CREATE TABLE carbon_entries (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    category    VARCHAR(255) NOT NULL,
    type        VARCHAR(255) NOT NULL,
    co2         DOUBLE PRECISION NOT NULL,
    entry_date  TIMESTAMP NOT NULL,
    created_at  TIMESTAMP NOT NULL
);
CREATE INDEX idx_carbon_entry_user_id ON carbon_entries (user_id);

-- Health Logs
CREATE TABLE health_logs (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    type            VARCHAR(255) NOT NULL,
    steps           INTEGER,
    distance        DOUBLE PRECISION,
    workout_type    VARCHAR(255),
    duration        INTEGER,
    intensity       VARCHAR(255),
    calories        INTEGER,
    weight          DOUBLE PRECISION,
    height          DOUBLE PRECISION,
    body_fat        DOUBLE PRECISION,
    hours           DOUBLE PRECISION,
    quality         VARCHAR(255),
    bedtime         VARCHAR(255),
    wake_time       VARCHAR(255),
    water_ml        INTEGER,
    entry_date      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL
);
CREATE INDEX idx_health_log_user_id ON health_logs (user_id);

-- Products
CREATE TABLE products (
    id              BIGSERIAL PRIMARY KEY,
    seller_id       BIGINT NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    category        VARCHAR(255) NOT NULL,
    price           DOUBLE PRECISION NOT NULL,
    image_url       VARCHAR(255),
    eco_rating      INTEGER,
    is_secondhand   BOOLEAN,
    is_available    BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL
);

-- Cart Items
CREATE TABLE cart_items (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    product_id  BIGINT NOT NULL,
    quantity    INTEGER DEFAULT 1 NOT NULL,
    created_at  TIMESTAMP NOT NULL
);
CREATE INDEX idx_cart_item_user_id ON cart_items (user_id);

-- Orders
CREATE TABLE orders (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    total_price      DOUBLE PRECISION NOT NULL,
    status           VARCHAR(255) NOT NULL,
    payment_method   VARCHAR(255) NOT NULL,
    shipping_address VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL
);
CREATE INDEX idx_order_user_id ON orders (user_id);

-- Order Items
CREATE TABLE order_items (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT NOT NULL,
    product_id   BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity     INTEGER NOT NULL,
    price        DOUBLE PRECISION NOT NULL,
    created_at   TIMESTAMP NOT NULL
);
CREATE INDEX idx_order_item_order_id ON order_items (order_id);

-- Notes
CREATE TABLE notes (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    title       VARCHAR(255) NOT NULL,
    body        TEXT,
    tag         VARCHAR(255),
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL
);
CREATE INDEX idx_note_user_id ON notes (user_id);

-- Achievements
CREATE TABLE achievements (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(255) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    icon        VARCHAR(255),
    category    VARCHAR(255),
    threshold   INTEGER,
    created_at  TIMESTAMP NOT NULL
);

-- User Achievements
CREATE TABLE user_achievements (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    achievement_id  BIGINT NOT NULL,
    unlocked_at     TIMESTAMP NOT NULL,
    CONSTRAINT uk_user_achievement UNIQUE (user_id, achievement_id)
);
CREATE INDEX idx_user_achievement_user_id ON user_achievements (user_id);
CREATE INDEX idx_user_achievement_achievement_id ON user_achievements (achievement_id);
