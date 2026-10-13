import test from 'node:test';
import assert from 'node:assert/strict';
import { groupPhones } from '../src/phone-groups.js';

const ha = { service: 'mobile_app_x', tracker: 'device_tracker.x', name: 'XITOL', model: 'Pixel 10 Pro XL', on: true };
const cd = { service: 'mobile_app_jamie', tracker: 'device_tracker.j', name: 'Jamie Pixel 10 Pro XL', model: 'Pixel 10 Pro XL', own_app: true, on: true };
const ipad = { service: 'mobile_app_ipad', tracker: 'device_tracker.i', name: 'iPad', model: 'iPad17,4', apple: true, on: false };

test('the Church Drive app folds into the Home Assistant app on the same phone', () => {
  const rows = groupPhones([ha, cd, ipad]);
  assert.equal(rows.length, 2);
  assert.equal(rows[0].main.name, 'XITOL');
  assert.equal(rows[0].also.name, 'Jamie Pixel 10 Pro XL');
  assert.equal(rows[1].also, null);
});

test('a Church Drive app phone with nothing beside it is listed on its own', () => {
  const rows = groupPhones([cd, ipad]);
  assert.deepEqual(rows.map((r) => r.main.name), ['iPad', 'Jamie Pixel 10 Pro XL']);
});

test('one Church Drive app folds into only one phone', () => {
  const ha2 = { ...ha, service: 'mobile_app_y', tracker: 'device_tracker.y', name: 'Other Pixel' };
  const rows = groupPhones([ha, ha2, cd]);
  assert.equal(rows.filter((r) => r.also).length, 1);
});

test('no phones is fine', () => {
  assert.deepEqual(groupPhones(undefined), []);
});
