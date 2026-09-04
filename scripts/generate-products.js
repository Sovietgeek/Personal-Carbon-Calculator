/**
 * EcoVerse — 300+ Product SQL Generator
 * Generates realistic eco product seed data for the products table.
 */
const fs = require('fs');

const categories = {
  kitchen: { brands: ['EcoSoul', 'Bamboo India', 'Trijal', 'RawNature', 'Jucare', 'KansaCraft', 'ClayBee', 'Beco', 'The Better Home', 'GreenDust'] },
  beauty: { brands: ['Neemli', 'Arata', 'PureGlow', 'BareNecessities', 'Svasty', 'VedicWhisper', 'OrganicTattva', 'GreenHeal', 'Tavam', 'Nirbha'] },
  fashion: { brands: ['GreenDust', 'EcoTrunk', 'JuteBox', 'Bamboo India', 'OrganicTattva', 'BareNecessities', 'EcoSoul', 'GreenBag', 'RawNature', 'Saahas'] },
  cleaning: { brands: ['Beco', 'The Better Home', 'Zerodor', 'CleanEdge', 'EcoSoul', 'GreenHeal', 'Tavam', 'Jucare', 'SunMango', 'BareNecessities'] },
  zerowaste: { brands: ['Beco', 'Zerodor', 'EcoSoul', 'Bamboo India', 'BeeWrap', 'GreenDust', 'SunMango', 'Saahas', 'Earthist', 'HappyTusli'] },
  garden: { brands: ['SeedPaper', 'Earthist', 'GreenHeal', 'Dharaksha', 'HappyTusli', 'Beejom', 'OrganicTattva', 'Saahas', 'EcoTrunk', 'Trijal'] },
  tech: { brands: ['SolarTech', 'SmartLED', 'EcoVolt', 'GreenDust', 'EcoSoul', 'SunMango', 'Tavam', 'Nirbha', 'BareNecessities', 'GreenHeal'] },
  solar: { brands: ['SolarTech', 'SunMango', 'EcoVolt', 'GreenHeal', 'Tavam', 'SmartLED', 'EcoSoul', 'GreenDust', 'BareNecessities', 'Nirbha'] },
  water: { brands: ['HydroLife', 'Trijal', 'EcoSoul', 'AquaPure', 'Jucare', 'GreenHeal', 'Mountain Valley', 'BareNecessities', 'RawNature', 'Earthist'] },
  transport: { brands: ['EcoVolt', 'GreenDust', 'EcoSoul', 'Tavam', 'BareNecessities', 'SunMango', 'Nirbha', 'Saahas', 'GreenHeal', 'Earthist'] },
  energy: { brands: ['EcoVolt', 'SolarTech', 'SmartLED', 'SunMango', 'GreenHeal', 'EcoSoul', 'Tavam', 'BareNecessities', 'Nirbha', 'GreenDust'] },
  books: { brands: ['EcoPaper', 'SeedPaper', 'GreenHeal', 'EcoSoul', 'BareNecessities', 'Saahas', 'Earthist', 'GreenDust', 'Nirbha', 'VedicWhisper'] },
  offset: { brands: ['GreenHeal', 'EcoSoul', 'SeedPaper', 'Dharaksha', 'Earthist', 'Saahas', 'GreenDust', 'BareNecessities', 'SunMango', 'EcoTrunk'] }
};

const productNames = {
  kitchen: [
    'Bamboo Cutlery Set (12-Piece)', 'Stainless Steel Lunch Box', 'Glass Meal Prep Containers (5-Pack)',
    'Coconut Shell Bowls (Set of 4)', 'Neem Wood Spatula Set', 'Reusable Silicone Food Lids (6-Pack)',
    'Bamboo Chopping Board', 'Steel Straw Set (6-Piece)', 'Compostable Paper Plates (50-Pack)',
    'Clay Pot Set (Handmade)', 'Copper Water Bottle (Handcrafted)', 'Bamboo Steam Basket',
    'Organic Cotton Kitchen Towels (3-Pack)', 'Stainless Steel Tiffin Carrier', 'Wooden Salad Bowl Set',
    'Beeswax Food Wraps (Value Pack)', 'Cast Iron Tawa', 'Eco Dish Brush Set',
    'Bamboo Salt & Pepper Shakers', 'Jute Shopping Bag (Foldable)', 'Steel Lunch Box (Leakproof)',
    'Silicone Baking Mat (Reusable)', 'Glass Food Storage Jars (5-Pack)', 'Bamboo Serving Tray',
    'Organic Cotton Apron', 'Stainless Steel Water Bottle 1L', 'Wooden Rolling Pin',
    'Coconut Fiber Scrubber (3-Pack)', 'Bamboo Tea Strainer', 'Clay Tea Set (6-Piece)',
    'Reusable Produce Bags (Cotton)', 'Bamboo Utensil Holder', 'Cast Iron Dosa Tawa',
    'Steel Colander (Eco)', 'Organic Linen Napkins (Set of 4)', 'Bamboo Spice Jars (6-Pack)',
    'Insulated Eco Lunch Bag', 'Stainless Steel Mixing Bowls', 'Wooden Cheese Board',
    'Bamboo Drawer Organizer'
  ],
  beauty: [
    'Bamboo Toothbrush (4-Pack)', 'Organic Neem Face Wash', 'Charcoal Soap Bar',
    'Aloe Vera Gel (Pure, 200ml)', 'Coconut Oil Hair Mask', 'Sandalwood Face Pack',
    'Organic Lip Balm (Set of 3)', 'Turmeric Face Scrub', 'Rose Water Toner (100ml)',
    'Reusable Bamboo Cotton Rounds', 'Shea Butter Body Lotion', 'Himalayan Salt Scrub',
    'Organic Green Tea Face Mask', 'Natural Vitamin C Serum', 'Almond Oil Hair Oil',
    'Bamboo Hair Brush', 'Lavender Essential Oil (10ml)', 'Tea Tree Oil Face Wash',
    'Organic Multani Mitti Face Pack', 'Coconut & Jasmine Body Butter', 'Neem & Tulsi Soap (3-Pack)',
    'Bamboo Razor (Reusable)', 'Rosehip Seed Oil (15ml)', 'Organic Coffee Body Scrub',
    'Natural Calendula Cream', 'Bamboo Combs (Set of 2)', 'Cucumber Face Mist',
    'Organic Jojoba Oil (30ml)', 'Sulfur-Free Shampoo Bar', 'Natural Deodorant Stick',
    'Bamboo Makeup Brush Set', 'Kokum Butter Body Cream', 'Organic Hibiscus Hair Mask',
    'Bamboo Toothbrush Travel Case', 'Soy-Based Nail Polish Remover'
  ],
  fashion: [
    'Organic Cotton T-Shirt (Unisex)', 'Hemp Fiber Tote Bag', 'Bamboo Fiber Socks (3-Pack)',
    'Recycled Polyester Jacket', 'Jute Handbag (Handwoven)', 'Organic Linen Shirt (Men)',
    'Khadi Cotton Scarf', 'Bamboo Sunglasses (Wooden)', 'Cork Wallet (Minimalist)',
    'Organic Cotton Kurta (Women)', 'Hemp Sandals (Summer)', 'Upcycled Sari Scarf',
    'Organic Cotton Yoga Pants', 'Jute Backpack (Rustic)', 'Hemp Belt (Eco)',
    'Organic Cotton Baby Romper', 'Vegan Cork Belt', 'Handloom Stole (Cotton)',
    'Reusable Bamboo Face Mask', 'Organic Cotton Underwear (3-Pack)',
    'Hemp Messenger Bag', 'Recycled PET Backpack', 'Khadi Face Mask (Cotton)',
    'Organic Cotton Hoodie', 'Cork Yoga Mat Bag', 'Jute Slippers (Handmade)',
    'Bamboo Fiber Bathrobe', 'Organic Cotton Denim Jacket', 'Hemp Rope Bracelet (Set of 3)',
    'Recycled Plastic Beach Tote', 'Handloom Cotton Saree', 'Vegan Cork Sandals',
    'Bamboo Sunglasses Case', 'Organic Cotton Beanie', 'Hemp Shoulder Bag'
  ],
  cleaning: [
    'Natural Dish Soap (Lemon, 500ml)', 'Bamboo Cleaning Brush Set', 'Vinegar All-Purpose Cleaner',
    'Reusable Microfiber Cloths (10-Pack)', 'Baking Soda Natural Cleaner', 'Bamboo Toilet Brush',
    'Eucalyptus Castile Soap (1L)', 'Lemongrass Floor Cleaner', 'Compostable Sponge (3-Pack)',
    'Reusable Glass Spray Bottle', 'Natural Laundry Detergent (1kg)', 'Bamboo Clothesline',
    'Organic Wool Dryer Balls (4-Pack)', 'Citrus Degreaser (Concentrate)', 'Bamboo Dustpan & Brush',
    'Tea Tree Toilet Cleaner', 'Bamboo Scrub Brush', 'Lavender Linen Spray (100ml)',
    'Natural Rubber Cleaning Gloves', 'Coconut Fibre Scourer (5-Pack)', 'Bamboo Dish Rack',
    'Organic Dishwasher Tablets', 'Enzyme Stain Remover (Natural)', 'Bamboo Laundry Basket',
    'Neem Wood Polish (Natural)', 'Bamboo Soap Dispenser', 'Compostable Trash Bags (20-Pack)',
    'Bacteria-Based Drain Cleaner', 'Bamboo Countertop Organizer', 'Essential Oil Air Freshener'
  ],
  zerowaste: [
    'Stainless Steel Straws (4-Pack)', 'Beeswax Food Wraps (Set of 3)', 'Reusable Produce Bags (5-Pack)',
    'Adult Bamboo Toothbrush', 'Glass Water Bottle (500ml)', 'Leakproof Metal Lunch Box',
    'Cotton Mesh Produce Bags', 'Stainless Steel Safety Razor', 'Bamboo Cutlery Travel Set',
    'Bamboo Reusable Coffee Cup', 'Silicone Stretch Lids (4-Pack)', 'Handmade Jute String Bag',
    'Stainless Steel Flask (750ml)', 'Bamboo Straw Cleaning Brush', 'Glass Food Container (Set of 3)',
    'Cotton Sandwich Wrap (Reusable)', 'Bamboo Soap Dish', 'Compostable Bamboo Toothbrush',
    'Metal Razor Blades (50-Pack)', 'Bamboo Travel Bottles (Set of 3)', 'Reusable Silicone Pouch',
    'Stainless Steel 2-Tier Tiffin', 'Reusable Cotton Swabs (10-Pack)', 'Bamboo Lip Balm Container',
    'Multipurpose Glass Spray Bottle', 'Bamboo Makeup Remover Pads', 'Cotton Coffee Filter (Reusable)',
    'Bamboo Storage Box (Small)', 'Silicone Menstrual Cup', 'Bamboo Toothbrush Stand'
  ],
  garden: [
    'Organic Tomato Seeds (5 Varieties)', 'Bamboo Planters (Set of 3)', 'Coconut Coir Pots (10-Pack)',
    'Neem Cake Fertilizer (1kg)', 'Bamboo Garden Kneeler', 'Kitchen Compost Bin (5L)',
    'Organic Seed Starter Kit', 'Bamboo Plant Labels (20-Pack)', 'Neem Oil Pest Repellent',
    'Bamboo Watering Can', 'Organic Mint Seeds (3 Varieties)', 'Coconut Fiber Erosion Mat',
    'Bamboo Vertical Garden Planter', 'Organic Soil Mix (5kg)', 'Expandable Bamboo Trellis',
    'Home Worm Composting Bin', 'Organic Chili Seeds (5 Varieties)', 'Bamboo Garden Stakes (20-Pack)',
    'Rainwater Harvesting Kit', 'Bamboo Bird Feeder', 'Wildflower Seed Mix (Organic)',
    'Coconet Biodegradable Net', 'Bamboo Herb Drying Rack', 'Compost Accelerator (1kg)',
    'Organic Coriander Seeds'
  ],
  tech: [
    'Solar Power Bank (10000mAh)', 'WiFi LED Smart Bulb (Color)', 'Bamboo Wireless Charger',
    'Smart Plug Energy Monitor', 'Foldable Solar USB Charger', 'Bamboo Phone Stand (Adjustable)',
    'Smart Thermostat (Eco Mode)', 'Ergonomic Bamboo Laptop Stand', 'Recycled Bluetooth Speaker',
    'Portable Solar Lantern', 'Bamboo Wired Keyboard', 'Bamboo Wireless Mouse',
    'Energy Saving Power Strip', 'Wooden Bamboo Watch', 'Solar Backpack Charger',
    'Bamboo Headphone Stand', 'USB-C LED Desk Lamp', 'Bamboo Cable Organizer',
    'Biodegradable Phone Case', 'Solar Path Light (4-Pack)', 'Bamboo Charging Station',
    'Energy Monitor Smart Plug', 'Bamboo Monitor Riser', 'Eco Mode LED Strip (5m)',
    'Desktop Bamboo Air Purifier'
  ],
  solar: [
    'Portable Solar Panel (100W)', 'Solar Garden Light (10-Pack)', 'Solar Water Heater (100L)',
    'Solar Charge Controller (30A)', 'Solar Inverter (1000W)', 'Rechargeable Solar Lantern',
    'Solar Charging Backpack', 'USB Solar Desk Fan', 'Solar Battery (12V 100Ah)',
    'Portable Solar Cooker', 'Solar Mobile Charger (10W)', 'Solar LED Street Light (50W)',
    'DC Solar Pump (1HP)', 'Solar Fence Light (6-Pack)', 'Solar AC/DC Converter',
    'Solar Panel Cleaning Kit', 'Waterproof Solar Extension Cord', 'Solar Power Meter',
    'Solar Pond Water Fountain', 'Solar USB Hub (4-Port)'
  ],
  water: [
    'Charcoal Water Filter Pitcher', 'Stainless Steel Water Bottle (750ml)', 'Countertop Bamboo Water Filter',
    'Copper Water Jug (Ayurvedic)', 'Glass Water Bottle (1L Leakproof)', 'Carbon Filter Cartridges (3-Pack)',
    'Bamboo Tabletop Dispenser', 'Silicone Reusable Water Balloon', 'Stainless Steel Sippy Cup',
    'Terracotta Clay Water Filter', 'Collapsible Silicone Water Bottle', 'Insulated Bamboo Water Bottle',
    'Gravity Water Filter (5L)', 'Copper Tumbler (Set of 4)', 'Activated Carbon Pitcher',
    'Bamboo Infuser Bottle', 'Stainless Steel Canteen (1L)', 'Glass Water Carafe with Lid',
    'Bamboo Water Filter Straw', 'Stainless Steel Ice Cubes'
  ],
  transport: [
    'Bamboo City Bicycle', 'Electric Cycle Conversion Kit', 'Bamboo Bike Helmet',
    'Recycled Pannier Bag', 'Bamboo Bike Fenders', 'Smart E-Bike Charger',
    'Recycled Steel Chain Lock', 'Bamboo Front Basket', 'Solar Bicycle LED Light Set',
    'Eco Electric Scooter (250W)', 'Bamboo Handle Bike Pump', 'Bamboo Bike Phone Mount',
    'Bamboo Mudguards', 'Custom Bamboo Cargo Bike', 'Bamboo Kickstand',
    'Custom Bamboo Bike Frame', 'E-Rickshaw Conversion Kit', 'Bamboo Bottle Cage',
    'Eco Bamboo Skateboard', 'Bamboo Repair Stand'
  ],
  energy: [
    'Smart Home Energy Meter', 'Eco Programmable Thermostat', '6-Outlet Energy Power Strip',
    'Dimmable LED Desk Lamp (10W)', 'Standby Power Killer Plug', 'WiFi Home Energy Monitor',
    'Rapid Boil Eco Kettle (1L)', 'DC Motor Energy Fan (48W)', 'Smart Radiator Valve',
    'LED Ceiling Panel Light (20W)', 'Smart Energy App Monitor', 'Timer Smart Light Switch',
    'Instant Eco Water Heater (3L)', 'Solar Ready LED Flood Light', 'Smart Home Energy Hub',
    'LED Energy Bulbs (4-Pack)', 'PIR Occupancy Sensor', 'Smart Power Meter Plug',
    'Timer Controlled Eco Geyser', 'Solar Pump Controller'
  ],
  books: [
    'The Zero Waste Home (Book)', 'Eco Cooking: Sustainable Recipes', 'Climate Change Explained (Guide)',
    'Plastic-Free Living Handbook', 'The Green Myth (Environment)', 'Bamboo Architecture (Coffee Table)',
    'Sustainable Fashion Guide', 'Organic Gardening Manual', 'Clean Energy Revolution (Book)',
    'The Carbon Footprint Guide', 'Recycled Paper Notebooks (Set of 3)', 'Eco-Friendly Planner 2026',
    'Biodiversity of India (Illustrated)', 'Minimalist Lifestyle Guide', 'Climate Action Handbook',
    'Plantable Seed Paper Journal', 'Ocean Conservation Photo Book', 'Sustainable Travel Guide',
    'Eco Verse: The Story (Graphic Novel)', "Children's Eco Activity Book (Ages 5-10)"
  ],
  offset: [
    'Plant 10 Trees (Certificate)', 'Carbon Offset (1 Ton CO2)', 'Adopt a Tree (1 Year)',
    'Mangrove Plantation (5 Trees)', 'Support a Solar Village (1 Unit)', 'Clean Water Access (1 Person)',
    'Forest Restoration (100 sqm)', 'Wildlife Corridor Sponsor', 'Biochar Production (10kg)',
    'Ocean Plastic Cleanup (1kg)'
  ]
};

const descriptions = [
  'Handcrafted from sustainable materials, perfect for eco-conscious living.',
  'Made from 100% natural, biodegradable materials with zero plastic packaging.',
  'Certified organic and ethically sourced. Supports fair trade practices.',
  'Durable, reusable, and designed to reduce single-use plastic waste.',
  'Eco-friendly alternative to everyday products. Compostable at end of life.',
  'Crafted by local artisans using traditional techniques and natural materials.',
  'Plastic-free packaging. Carbon-neutral shipping. 100% satisfaction guaranteed.',
  'Sustainably harvested raw materials. Minimal processing, maximum impact.',
  'Replace disposable items with this reusable, long-lasting alternative.',
  'Ayurvedic-inspired design using natural, therapeutic materials.',
  'Chemical-free and safe for the whole family. Gentle on the planet.',
  'Upcycled from post-consumer waste. Each piece is unique.',
  'Handwoven by skilled artisans. Supports rural livelihoods.',
  'Naturally antibacterial and eco-friendly. Easy to clean and maintain.',
  'Precision-crafted for daily use. Heirloom quality that lasts generations.',
  'Lightweight, portable, and perfect for on-the-go sustainable living.',
  'Biodegradable packaging included. No synthetic dyes or chemicals.',
  'Community-sourced materials. Every purchase supports reforestation.',
  'Tested for quality and durability. Satisfaction guaranteed with easy returns.',
  'Traditional craft meets modern design. Truly one-of-a-kind piece.'
];

const features_by_cat = {
  kitchen: ['100% Natural Materials', 'BPA-Free & Non-Toxic', 'Dishwasher Safe', 'Plastic-Free Packaging', 'Handcrafted Quality', 'Long-Lasting Durability'],
  beauty: ['100% Organic Ingredients', 'Cruelty-Free & Vegan', 'No Parabens or Sulfates', 'Dermatologically Tested', 'Plastic-Free Packaging', 'Natural Fragrance'],
  fashion: ['100% Organic Cotton', 'Fair Trade Certified', 'Handwoven by Artisans', 'Natural Dyes Only', 'Biodegradable Materials', 'Ethically Sourced'],
  cleaning: ['Non-Toxic Formula', 'Plant-Based Ingredients', 'Biodegradable', 'No Harsh Chemicals', 'Safe for Septic Systems', 'Concentrated Formula'],
  zerowaste: ['Reusable Design', 'Plastic-Free', 'Compostable at End of Life', 'Space-Saving', 'Lightweight & Portable', 'Easy to Clean'],
  garden: ['Organic Seeds', 'Non-GMO', 'Heirloom Varieties', 'Natural Pest Control', 'Water Efficient', 'Supports Biodiversity'],
  tech: ['Energy Efficient', 'Solar Compatible', 'Recycled Materials', 'Low Power Consumption', 'Smart Features', 'USB-C Charging'],
  solar: ['High Efficiency Cells', 'Weatherproof Design', 'Easy Installation', '5 Year Warranty', 'Zero Emissions', 'Portable & Lightweight'],
  water: ['BPA-Free', 'Leakproof Design', 'Insulated Option', 'Easy to Clean', 'Durable Construction', 'Natural Materials'],
  transport: ['Zero Emissions', 'Lightweight Frame', 'Low Maintenance', 'Eco-Friendly Materials', 'Smart Features', 'Energy Efficient'],
  energy: ['Energy Saving Mode', 'Smart Monitoring', 'Easy Installation', 'Reduces Power Bills', 'Long Lifespan', 'Quiet Operation'],
  books: ['100% Recycled Paper', 'Soy-Based Ink', 'Plantable Cover', 'Eco-Friendly Binding', 'Made in India', 'Supports Literacy'],
  offset: ['Verified Carbon Credits', 'Certified Offset', 'Supports Local Communities', '100% Impact', 'Transparent Tracking', 'Tax Deductible']
};

function random(m, M) { return Math.floor(Math.random() * (M - m + 1)) + m; }
function pick(arr) { return arr[random(0, arr.length - 1)]; }
function pickN(arr, n) { const s = new Set(); while (s.size < n) s.add(pick(arr)); return [...s]; }

let id = 11;
const rows = [];

for (const [cat, info] of Object.entries(categories)) {
  const names = productNames[cat];
  const featOpts = features_by_cat[cat];
  const secondhandPool = cat === 'fashion' ? [true, false, false, false, false] : (cat === 'books' ? [true, false, false] : [false, false, false, false, false]);

  for (const name of names) {
    const priceRange = { offset: [99, 1999], solar: [499, 15000], tech: [299, 5999], transport: [999, 25000], energy: [199, 4999], water: [199, 2999], fashion: [199, 2999], beauty: [99, 1999], kitchen: [149, 2999], cleaning: [99, 1499], zerowaste: [99, 1999], garden: [99, 2499], books: [99, 1499] }[cat] || [99, 999];
    const discount = random(5, 45);
    const mrp = random(priceRange[0], priceRange[1]);
    const price = Math.round(mrp * (100 - discount)) / 100;

    const brand = pick(info.brands);
    const desc = pick(descriptions);
    const ecoRating = random(3, 5);
    const isSecondhand = pick(secondhandPool);
    const stock = random(20, 200);
    const rating = (3.0 + Math.random() * 1.9).toFixed(2);
    const ratingCount = random(5, 500);
    const deliveryDays = random(3, 8);
    const weight = cat === 'solar' ? random(500, 5000) : cat === 'books' ? random(150, 600) : cat === 'fashion' ? random(100, 500) : cat === 'tech' ? random(100, 2000) : cat === 'kitchen' ? random(100, 1500) : random(30, 1000);

    const features = pickN(featOpts, 3);
    const featJson = '[' + features.map(f => '"' + f.replace(/"/g, '') + '"').join(',') + ']';

    const highlights = [];
    if (discount >= 25) highlights.push('Best Seller');
    if (ecoRating >= 5) highlights.push('Eco Pick');
    if (price >= 499) highlights.push('Free Delivery');
    if (isSecondhand) highlights.push('Pre-Owned Deal');
    const highlightStr = highlights.length > 0 ? highlights.join(',') : 'Popular';

    const tags = name.toLowerCase().replace(/[^a-z0-9 ]/g, '').split(' ').filter(t => t.length > 2).slice(0, 4).join(',') + ',' + cat + ',eco';

    rows.push(`(${id}, 1, '${name.replace(/'/g, "''")}', '${desc.replace(/'/g, "''")}', '${cat}', ${price.toFixed(2)}, NULL, ${ecoRating}, ${isSecondhand}, TRUE, ${stock}, 'ACTIVE', 0, NOW(), NOW(), '${brand}', ${mrp.toFixed(2)}, ${discount}, '${featJson}', '${highlightStr}', '${tags}', ${rating}, ${ratingCount}, ${deliveryDays}, ${weight})`);
    id++;
  }
}

const sql = `-- ================================================================\n-- ECOVERSE — V20: Seed 300+ Real Eco Products\n-- ${rows.length} products auto-generated across 13 eco categories\n-- ================================================================\n\nINSERT INTO products (id, seller_id, name, description, category, price, image_url, eco_rating, is_secondhand, is_available, stock, status, version, created_at, updated_at, brand, mrp, discount_percent, features, highlights, tags, rating, rating_count, delivery_days, weight_grams) VALUES\n${rows.join(',\n')}\nON CONFLICT DO NOTHING;\n\nSELECT setval('products_id_seq', (SELECT MAX(id) FROM products));\n`;

fs.writeFileSync('C:/Users/utkar/Desktop/EcoVerse-Complete-Latest/V20__Seed_300_Products.sql', sql);
console.log('Generated ' + rows.length + ' products');
console.log('File size: ' + (sql.length / 1024).toFixed(1) + ' KB');
