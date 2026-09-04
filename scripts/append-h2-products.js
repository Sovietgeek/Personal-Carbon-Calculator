/**
 * Convert the PostgreSQL V20 INSERT SQL to H2-compatible MERGE statements
 * and append to data.sql
 */
const fs = require('fs');

const v20Path = 'C:/Users/utkar/Desktop/EcoVerse-Complete-Latest/ecoverse-backend/src/main/resources/db/migration/V20__Seed_300_Products.sql';
const dataSqlPath = 'C:/Users/utkar/Desktop/EcoVerse-Complete-Latest/ecoverse-backend/src/main/resources/data.sql';

let sql = fs.readFileSync(v20Path, 'utf8');

// Extract the VALUES rows between the INSERT line and "ON CONFLICT"
const insertMatch = sql.match(/INSERT INTO products[\s\S]*?VALUES\n([\s\S]*?)\nON CONFLICT DO NOTHING;/);
if (!insertMatch) {
    console.error('Could not find VALUES block');
    process.exit(1);
}

const rows = insertMatch[1]
    .split(/,\n(?=\()/)
    .map(r => r.trim().replace(/;$/, ''))
    .filter(r => r.startsWith('(') && r.endsWith(')'));

// Convert each row: NOW() -> CURRENT_TIMESTAMP
const converted = rows.map(r => r.replace(/NOW\(\)/g, 'CURRENT_TIMESTAMP'));

// Build the H2 MERGE statement
// The products table now includes the new columns from V19 (created via Hibernate ddl-auto in H2)
const cols = 'id, seller_id, name, description, category, price, image_url, eco_rating, is_secondhand, is_available, stock, status, version, created_at, updated_at, brand, mrp, discount_percent, features, highlights, tags, rating, rating_count, delivery_days, weight_grams';

const mergeBlock = `\n-- EcoVerse Catalog: ${converted.length} real eco products (auto-generated)\nMERGE INTO products (${cols}) KEY(id) VALUES\n${converted.join(',\n')};\n`;

fs.appendFileSync(dataSqlPath, mergeBlock);
console.log('Appended ' + converted.length + ' products (MERGE) to data.sql');
