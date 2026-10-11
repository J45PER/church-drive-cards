import test from 'node:test';
import assert from 'node:assert/strict';
import { shoppingIdOf, shoppingNote, splitItems } from '../src/shopping-logic.js';

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
