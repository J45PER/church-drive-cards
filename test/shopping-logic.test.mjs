import test from 'node:test';
import assert from 'node:assert/strict';
import { shoppingIdOf, shoppingNote, splitItems, mapsUrl, samePlace } from '../src/shopping-logic.js';

test('finds the shopping list a task points at', () => {
  assert.equal(shoppingIdOf('• Milk\n• Eggs\nchurch-drive-shopping:5263e8a60d8d'), '5263e8a60d8d');
  assert.equal(shoppingIdOf('Mon 09:00 · for Hayley'), '');
  assert.equal(shoppingIdOf(undefined), '');
});

test('splits what was typed into items', () => {
  assert.deepEqual(splitItems('milk, eggs\n bread ;'), ['milk', 'eggs', 'bread']);
  assert.deepEqual(splitItems('   '), []);
});

test('says how many are left under a shopping task', () => {
  assert.equal(shoppingNote('• Milk\n• Eggs\nchurch-drive-shopping:5263e8a60d8d'), '2 to get');
  assert.equal(shoppingNote('church-drive-shopping:5263e8a60d8d'), 'Shopping list');
});

test('mapsUrl starts directions to the place', () => {
  assert.equal(mapsUrl({ lat: 35.01, lon: 34.05 }), 'https://www.google.com/maps/dir/?api=1&destination=35.01,34.05');
});

test('samePlace treats a repeat of the same spot as one place', () => {
  assert.equal(samePlace({ lat: 35.01, lon: 34.05 }, { lat: 35.010001, lon: 34.050001 }), true);
  assert.equal(samePlace({ lat: 35.01, lon: 34.05 }, { lat: 35.02, lon: 34.05 }), false);
});
