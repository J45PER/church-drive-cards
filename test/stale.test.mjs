import test from 'node:test';
import assert from 'node:assert/strict';
import { isStale } from '../src/stale.js';

test('a page on an older Church Drive than the one installed is stale', () => {
  assert.equal(isStale('v0.43.1', '0.43.0'), true);
  assert.equal(isStale('0.43.1', 'v0.43.1'), false);
  assert.equal(isStale('v0.43.1', '0.43.1'), false);
});

test('nothing is said when either version is unknown or not a release', () => {
  assert.equal(isStale(undefined, '0.43.1'), false);
  assert.equal(isStale('v0.43.1', ''), false);
  assert.equal(isStale('a1b2c3d', '0.43.1'), false);
});
