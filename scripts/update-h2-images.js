/**
 * Updates H2 data.sql — replaces NULL image_url with real Unsplash URLs
 * matching the same URLs as V21__Product_Images.sql (PostgreSQL migration)
 */
const fs = require('fs');
const path = require('path');

const DATA_SQL = path.join(__dirname, '..', 'ecoverse-backend', 'src', 'main', 'resources', 'data.sql');

// Category image mapping — same URLs as V21 migration
const categoryImages = {
  kitchen: [
    'https://images.unsplash.com/photo-1558618666-fcd25c85f82e?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1585752649550-4a8e5e54f843?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1590767257846-3070b2323fb5?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1607116667981-4974f3a5e1c0?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1584568694244-14fbdf83bd2e?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1585515320310-2598143947e1?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1594226201347-7c5eeb7263e0?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1570222094114-d054a8e1d56d?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1563729784474-d77dbb933a31?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1585515313258-6dbc2d5a347a?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1608566873201-9a47e44f7bde?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1556909114-44e3e7009e2e?w=400&h=400&fit=crop',
  ],
  beauty: [
    'https://images.unsplash.com/photo-1556228578-0d85b1a4d571?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1598440947623-d3a82567b251?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1608248543803-ba4f8c70ae22?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1571781926291-c4779fd0b76b?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1617897653290-b5314eb0e25d?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1596462502278-27bfdc403348?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1570194065650-d99fb4d38691?w=400&h=400&fit=crop',
  ],
  fashion: [
    'https://images.unsplash.com/photo-1523381210434-271e8be1f52b?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1445205170236-053b83016027?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1558618666-fcd25c85f82e?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1489987707025-afc232f7ea0f?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1467043237213-65f2da533964?w=400&h=400&fit=crop',
  ],
  cleaning: [
    'https://images.unsplash.com/photo-1585421514738-01798e348b17?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1558618666-fcd25c85f82e?w=400&h=400&fit=crop',
  ],
  zerowaste: [
    'https://images.unsplash.com/photo-1542601906990-b4d3fb778b09?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1596462502278-27bfdc403348?w=400&h=400&fit=crop',
  ],
  garden: [
    'https://images.unsplash.com/photo-1416879595882-3373a0480b5b?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1585320806297-9794b3e39323?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1466692478068-cebfb9e4957b?w=400&h=400&fit=crop',
  ],
  tech: [
    'https://images.unsplash.com/photo-1518770660439-4636190af475?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1496185106368-308d237b5791?w=400&h=400&fit=crop',
  ],
  solar: [
    'https://images.unsplash.com/photo-1509391366360-f2db959d6944?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1532604840-8a646c0a4c16?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1508514177221-188d1c53f287?w=400&h=400&fit=crop',
  ],
  water: [
    'https://images.unsplash.com/photo-1548839140-29a749e1cf1d?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1602143407151-7111542a6e0d?w=400&h=400&fit=crop',
  ],
  transport: [
    'https://images.unsplash.com/photo-1571068316344-75bc76f77890?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1532298224535-3311b7e9c5de?w=400&h=400&fit=crop',
  ],
  energy: [
    'https://images.unsplash.com/photo-1558618666-fcd25c85f82e?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1560972814-b4a4c3c6b0e1?w=400&h=400&fit=crop',
  ],
  books: [
    'https://images.unsplash.com/photo-1512820790803-83ca7d1e3f0e?w=400&h=400&fit=crop',
    'https://images.unsplash.com/photo-1481627834876-b7833e8f5570?w=400&h=400&fit=crop',
  ],
  offset: [
    'https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=400&h=400&fit=crop',
  ],
};

// Category counters for cycling images
const counters = {};
for (const cat of Object.keys(categoryImages)) counters[cat] = 0;

function getImageUrl(category) {
  const images = categoryImages[category];
  if (!images || images.length === 0) return categoryImages.kitchen[0];
  const idx = counters[category] % images.length;
  counters[category]++;
  return images[idx];
}

let content = fs.readFileSync(DATA_SQL, 'utf-8');
let updated = 0;

// Match MERGE INTO products ... VALUES lines
// The image_url is the 7th field in the VALUES tuple (after id, seller_id, name, description, category, price)
// Pattern: (ID, SELLER_ID, 'name', 'desc', 'category', PRICE, NULL, ...
content = content.replace(
  /\((\d+),\s*(\d+|NULL),\s*'([^']*)',\s*'([^']*)',\s*'([^']+)',\s*([\d.]+),\s*NULL,/g,
  (match, id, sellerId, name, desc, category, price) => {
    const url = getImageUrl(category);
    updated++;
    return `(${id}, ${sellerId}, '${name}', '${desc}', '${category}', ${price}, '${url}',`;
  }
);

fs.writeFileSync(DATA_SQL, content, 'utf-8');
console.log(`Updated ${updated} product image URLs in data.sql`);
