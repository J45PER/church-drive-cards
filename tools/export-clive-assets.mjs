// Writes the two files Home Assistant serves for Clive's icons and cards:
//   custom_components/church_drive/frontend/clive/icons.json   (from design/clive/house-icons.json)
//   custom_components/church_drive/frontend/clive/cards.json   (from design/clive/cards.json)
// Served at /church_drive/clive/icons.json and /church_drive/clive/cards.json (the integration already serves that folder).
// `node tools/export-clive-assets.mjs --check` fails if the served files are out of date (CI), without writing.
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';

const OUT = 'custom_components/church_drive/frontend/clive';
const { version } = JSON.parse(readFileSync('package.json', 'utf8'));
const check = process.argv.includes('--check');

const house = JSON.parse(readFileSync('design/clive/house-icons.json', 'utf8'));
const cards = JSON.parse(readFileSync('design/clive/cards.json', 'utf8'));

// An icon that is only a dotted placeholder box has not been drawn yet: listed as pending, not served.
const icons = {}, pending = [];
for (const [code, v] of Object.entries(house.icons)) {
  if (v.p.includes('stroke-dasharray')) { pending.push(code); continue; }
  icons[code] = { n: v.n, s: v.s, p: v.p };
}
const namespaces = {};
for (const code of Object.keys(icons)) {
  const ns = code.includes(':') ? code.split(':')[0] : code.split('_')[0].split(' ')[0];
  namespaces[ns] = (namespaces[ns] || 0) + 1;
}

const iconsOut = { version: 1, app: version, grid: house.grid, source: house.source, namespaces, count: Object.keys(icons).length, pending, icons };
const cardsOut = { version: 1, app: version, ...cards };

const files = { 'icons.json': JSON.stringify(iconsOut), 'cards.json': JSON.stringify(cardsOut, null, 1) };
let stale = false;
for (const [name, text] of Object.entries(files)) {
  const path = `${OUT}/${name}`;
  if (check) {
    if (!existsSync(path) || readFileSync(path, 'utf8') !== text + '\n') { console.error(`${path} is out of date: run node tools/export-clive-assets.mjs`); stale = true; }
  } else {
    mkdirSync(OUT, { recursive: true });
    writeFileSync(path, text + '\n');
    console.log(`${path}: ${(text.length / 1024).toFixed(0)} KB`);
  }
}
if (check && stale) process.exit(1);
if (!check) console.log(`${iconsOut.count} icons served, ${pending.length} still to draw (${Object.entries(namespaces).map(([k, v]) => `${k} ${v}`).join(', ')})`);
