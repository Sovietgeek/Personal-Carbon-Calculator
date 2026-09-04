-- ================================================================
-- ECOVERSE — V19: Product Enhanced Fields
-- Adds Amazon-like product details: brand, MRP, discount, features, etc.
-- ================================================================

-- Brand / manufacturer name
ALTER TABLE products ADD COLUMN IF NOT EXISTS brand VARCHAR(100);

-- Original MRP before discount (NULL = no discount shown)
ALTER TABLE products ADD COLUMN IF NOT EXISTS mrp NUMERIC(12,2);

-- Discount percentage (0-99)
ALTER TABLE products ADD COLUMN IF NOT EXISTS discount_percent INTEGER;

-- JSON array of feature strings
ALTER TABLE products ADD COLUMN IF NOT EXISTS features TEXT;

-- Comma-separated highlights for product card
ALTER TABLE products ADD COLUMN IF NOT EXISTS highlights VARCHAR(500);

-- Comma-separated search tags
ALTER TABLE products ADD COLUMN IF NOT EXISTS tags VARCHAR(500);

-- Average customer rating 1.0-5.0
ALTER TABLE products ADD COLUMN IF NOT EXISTS rating NUMERIC(3,2) DEFAULT 4.00;

-- Number of customer ratings
ALTER TABLE products ADD COLUMN IF NOT EXISTS rating_count INTEGER DEFAULT 0;

-- Estimated delivery days
ALTER TABLE products ADD COLUMN IF NOT EXISTS delivery_days INTEGER DEFAULT 5;

-- Product weight in grams
ALTER TABLE products ADD COLUMN IF NOT EXISTS weight_grams INTEGER;

-- Add CHECK constraints
ALTER TABLE products ADD CONSTRAINT chk_products_discount CHECK (discount_percent IS NULL OR (discount_percent >= 0 AND discount_percent <= 99));
ALTER TABLE products ADD CONSTRAINT chk_products_rating CHECK (rating >= 1.0 AND rating <= 5.0);
ALTER TABLE products ADD CONSTRAINT chk_products_rating_count CHECK (rating_count >= 0);
ALTER TABLE products ADD CONSTRAINT chk_products_delivery_days CHECK (delivery_days > 0);
ALTER TABLE products ADD CONSTRAINT chk_products_weight CHECK (weight_grams IS NULL OR weight_grams > 0);

-- Update existing 10 products with enhanced data
UPDATE products SET brand = 'EcoVerse', mrp = 399, discount_percent = 25, rating = 4.20, rating_count = 24, delivery_days = 5, tags = 'bamboo,toothbrush,biodegradable,plastic-free,oral-care', features = '["100% Biodegradable Bamboo","Zero Plastic Packaging","Soft Bristles","Set of 4 Brushes"]', highlights = 'Best Seller,Eco Pick' WHERE id = 1;
UPDATE products SET brand = 'GreenBag', mrp = 599, discount_percent = 25, rating = 4.50, rating_count = 89, delivery_days = 3, tags = 'cotton,tote,bag,reusable,shopping,plastic-free', features = '["100% Organic Cotton","Replaces 700+ Plastic Bags","Machine Washable","Reinforced Handles"]', highlights = 'Top Rated' WHERE id = 2;
UPDATE products SET brand = 'SolarTech', mrp = 2999, discount_percent = 17, rating = 4.00, rating_count = 156, delivery_days = 7, tags = 'solar,power-bank,charger,renewable-energy,portable', features = '["20000mAh Capacity","Solar + USB Charging","Charges 4-5 Phones","LED Flashlight"]', highlights = 'Free Delivery' WHERE id = 3;
UPDATE products SET brand = 'EcoPaper', mrp = 499, discount_percent = 20, rating = 4.70, rating_count = 45, delivery_days = 4, tags = 'recycled,notebook,paper,stationery,eco', features = '["100% Recycled Paper","Soy-Based Ink","Set of 3 Notebooks","Lay-Flat Binding"]', highlights = 'Eco Pick' WHERE id = 4;
UPDATE products SET brand = 'HydroLife', mrp = 999, discount_percent = 20, rating = 4.60, rating_count = 210, delivery_days = 3, tags = 'steel,bottle,insulated,BPA-free,hydration', features = '["750ml Capacity","Double-Wall Insulated","BPA-Free Stainless Steel","Keeps Cold 24hr / Hot 12hr"]', highlights = 'Best Seller,Free Delivery' WHERE id = 5;
UPDATE products SET brand = 'BeeWrap', mrp = 699, discount_percent = 22, rating = 4.30, rating_count = 67, delivery_days = 4, tags = 'beeswax,wraps,reusable,food-storage,plastic-free', features = '["Set of 3 Wraps (S/M/L)","Replaces Plastic Cling Film","Washable & Reusable","Lasts 12+ Months"]', highlights = 'Eco Pick' WHERE id = 6;
UPDATE products SET brand = 'ZenYoga', mrp = 699, discount_percent = 0, rating = 3.80, rating_count = 12, delivery_days = 5, tags = 'yoga,mat,eco,fitness,secondhand,pre-owned', features = '["6mm Thick Premium Mat","Eco-Friendly TPE Material","Non-Slip Surface","Carrying Strap Included"]', highlights = 'Pre-Owned Deal' WHERE id = 7;
UPDATE products SET brand = 'SmartLED', mrp = 2199, discount_percent = 14, rating = 4.10, rating_count = 134, delivery_days = 6, tags = 'LED,smart-bulb,WiFi,energy-saving,lighting', features = '["Pack of 4 WiFi Bulbs","75% Less Energy","16 Million Colors","Voice Control (Alexa/Google)"]', highlights = 'Energy Saver' WHERE id = 8;
UPDATE products SET brand = 'PureGlow', mrp = 1999, discount_percent = 20, rating = 4.40, rating_count = 78, delivery_days = 5, tags = 'organic,skincare,beauty,cruelty-free,natural', features = '["Complete Skincare Routine","Organic & Cruelty-Free","No Parabens or Sulfates","Dermatologically Tested"]', highlights = 'Top Rated' WHERE id = 9;
UPDATE products SET brand = 'EarthBin', mrp = 1499, discount_percent = 13, rating = 4.00, rating_count = 33, delivery_days = 7, tags = 'compost,bin,kitchen,waste,organic,recycling', features = '["Indoor Composting","Carbon Filter (No Smell)","Easy-Turn Handle","1 Year Warranty"]', highlights = 'Free Delivery' WHERE id = 10;
