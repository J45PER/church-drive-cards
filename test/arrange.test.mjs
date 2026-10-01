// Run with: npm test
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { balance, arrange } from '../src/arrange.js';

const G = 32;
const p = (h, more = {}) => ({ h, wide: false, full: false, hSpan: 0, hFull: 0, ...more });
const height = (items, a) => {
  const tot = a.cols.map((c) => c.reduce((s, i) => s + items[i].h, 0) + G * Math.max(0, c.length - 1));
  const top = Math.max(0, ...tot);
  let band = top;
  if (a.spans.length) {
    const others = Math.max(0, ...tot.filter((t, j) => j !== a.side));
    band = Math.max(tot[a.side], others + G + a.spans.reduce((s, i) => s + items[i].hSpan, 0) + G * (a.spans.length - 1));
  }
  return band + a.full.reduce((s, i) => s + G + items[i].hFull, 0);
};

test('balance still shares panels evenly', () => {
  assert.deepEqual(balance([100, 100, 100], 3, G), [[0], [1], [2]]);
  const cols = balance([300, 100, 100, 100], 2, G);
  assert.equal(cols.length, 2);
  assert.deepEqual(cols.flat().sort(), [0, 1, 2, 3]);
});

test('a short column gets a wide panel under it instead of a gap', () => {
  // Security on a laptop: Alarm short, Safety and Indoor cameras taller,
  // outdoor cameras and the zones wide.
  const items = [p(300), p(560), p(620), p(500, { wide: true, hSpan: 300, hFull: 260 }), p(620, { wide: true, hSpan: 190, hFull: 190 })];
  const a = arrange(items, 3, G);
  const allFull = { cols: [[0], [1], [2]], side: null, spans: [], full: [3, 4] };
  assert.ok(height(items, a) < height(items, allFull) - 100, `page is shorter: ${height(items, a)} vs ${height(items, allFull)}`);
  assert.deepEqual([...a.cols.flat(), ...a.spans, ...a.full].sort(), [0, 1, 2, 3, 4]);
});

test('a panel set to full width stays full width', () => {
  const items = [p(300), p(300), p(300), p(200, { full: true, hFull: 200 })];
  const a = arrange(items, 3, G);
  assert.deepEqual(a.full, [3]);
});

test('wide panels stay across the page when the columns are already level', () => {
  const items = [p(300), p(300), p(300), p(500, { wide: true, hSpan: 260, hFull: 200 })];
  const a = arrange(items, 3, G);
  assert.deepEqual(a.full, [3]);
  assert.deepEqual(a.spans, []);
});

test('one column keeps list order', () => {
  const items = [p(100), p(200, { wide: true, hSpan: 1, hFull: 1 }), p(100)];
  assert.deepEqual(arrange(items, 1, G).cols, [[0, 1, 2]]);
});

test('keeps the current arrangement unless the new one is clearly better', () => {
  const items = [p(300), p(310), p(305)];
  const prev = { cols: [[1], [0], [2]], side: null, spans: [], full: [] };
  assert.equal(arrange(items, 3, G, prev), prev);
});

test('two columns: a wide panel can go into a column', () => {
  const items = [p(200), p(700), p(300, { wide: true, hSpan: 0, hFull: 180 })];
  const a = arrange(items, 2, G);
  assert.equal(a.full.length, 0);
  assert.ok(height(items, a) <= 732);
});

test('a panel set to wide never goes into a column', () => {
  // Manager: Who's home, Device Health, the notifications table, Automatic to-dos.
  const items = [p(260), p(300), p(500, { wide: true, noCol: true, hSpan: 420, hFull: 380 }), p(760)];
  const a = arrange(items, 3, G);
  assert.ok(!a.cols.flat().includes(2));
  assert.deepEqual(a.spans, [2]);
  assert.equal(height(items, a), 760);
});
