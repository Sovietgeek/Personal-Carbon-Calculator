-- V22: Fix product images — replace broken Unsplash URLs with working picsum.photos URLs
-- Unsplash removed many older photos, causing 404 errors on all product images.
-- Using picsum.photos/seed/{category}-{id}/400/400 which generates deterministic,
-- always-available images unique to each product.

-- Replace ALL broken Unsplash URLs with seeded picsum URLs (each product gets a unique image)
UPDATE products
SET image_url = CONCAT('https://picsum.photos/seed/', category, '-', id, '/400/400')
WHERE image_url LIKE '%unsplash%';

-- Replace any NULL or empty image_url
UPDATE products
SET image_url = CONCAT('https://picsum.photos/seed/', category, '-', id, '/400/400')
WHERE image_url IS NULL OR image_url = '';
