import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const dir = 'custom_components/church_drive/frontend/clive';
const icons = JSON.parse(readFileSync(`${dir}/icons.json`, 'utf8'));
const cards = JSON.parse(readFileSync(`${dir}/cards.json`, 'utf8'));
const pkg = JSON.parse(readFileSync('package.json', 'utf8'));

test('the served files carry the app version', () => {
  assert.equal(icons.app, pkg.version);
  assert.equal(cards.app, pkg.version);
});

test('every served icon has path data and no placeholder boxes', () => {
  assert.ok(icons.count > 250);
  for (const [code, v] of Object.entries(icons.icons)) {
    assert.ok(v.p && v.p.includes(' d='), `${code} has path data`);
    assert.ok(!v.p.includes('stroke-dasharray'), `${code} is not a placeholder`);
  }
});

test('the icons the cards and animations name exist in the house set', () => {
  const names = new Set();
  for (const k of Object.values(cards.kinds)) if (typeof k.icon === 'string' && k.icon.startsWith('cd')) names.add(k.icon);
  for (const a of Object.values(cards.icons)) for (const n of [a.from, a.to, a.icon, a.start, a.end, ...(a.icons || [])]) if (n) names.add(n);
  for (const s of Object.values(cards.gregg.states)) names.add(s.icon);
  for (const n of names) assert.ok(icons.icons[n], `${n} is in icons.json`);
});

test('palettes are three hex colours and the morph recipes are valid', () => {
  for (const [name, p] of Object.entries(cards.palettes)) { assert.equal(p.length, 3, name); for (const c of p) assert.match(c, /^#[0-9A-Fa-f]{6}$/); }
  assert.ok(Object.keys(cards.morph.recipes).length > 30, 'every icon family has a morph recipe');
  for (const [k, r] of Object.entries(cards.morph.recipes)) assert.ok(!r.d || ['pop', 'drop', 'rise', 'left', 'right', 'spin'].includes(r.d), `${k} has a known arrival`);
});
