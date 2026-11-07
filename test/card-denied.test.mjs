import test from 'node:test';
import assert from 'node:assert/strict';
import { kitCardDenied } from '../src/card-kit.js';

test('an admin_only card is hidden from everyone but administrators', () => {
  const conf = { type: 'custom:overnight-heating-card', admin_only: true };
  assert.equal(kitCardDenied({ user: { is_admin: false }, states: {} }, conf), true);
  assert.equal(kitCardDenied({ user: {}, states: {} }, conf), true);
  assert.equal(kitCardDenied({ states: {} }, conf), true);
  assert.equal(kitCardDenied({ user: { is_admin: true }, states: {} }, conf), false);
});

test('a card without admin_only is not hidden by it', () => {
  assert.equal(kitCardDenied({ user: { is_admin: false }, states: {} }, { type: 'custom:fan-card' }), false);
});
