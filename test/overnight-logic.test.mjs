import test from 'node:test';
import assert from 'node:assert/strict';
import { hm, inWindow, stepTime, describeOvernight, modeOf, OVERNIGHT_MODES } from '../src/overnight-logic.js';

test('times are read as HH:MM', () => {
  assert.equal(hm('00:00:00'), '00:00');
  assert.equal(hm('6:5'), '06:05');
  assert.equal(hm('23:59:59'), '23:59');
  assert.equal(hm('24:00'), null);
  assert.equal(hm('unknown'), null);
  assert.equal(hm(null), null);
});

test('the window starts at the start and ends before the end, also across midnight', () => {
  assert.equal(inWindow('00:00', '06:00', '00:00'), true);
  assert.equal(inWindow('00:00', '06:00', '05:59'), true);
  assert.equal(inWindow('00:00', '06:00', '06:00'), false);
  assert.equal(inWindow('00:00', '06:00', '12:00'), false);
  assert.equal(inWindow('22:00', '06:00', '23:30'), true);
  assert.equal(inWindow('22:00', '06:00', '03:00'), true);
  assert.equal(inWindow('22:00', '06:00', '12:00'), false);
});

test('equal or unreadable times mean there is no window', () => {
  assert.equal(inWindow('06:00', '06:00', '06:00'), false);
  assert.equal(inWindow('unknown', '06:00', '03:00'), false);
});

test('a time moves by half an hour and wraps round the clock', () => {
  assert.equal(stepTime('00:00', 1), '00:30');
  assert.equal(stepTime('00:00', -1), '23:30');
  assert.equal(stepTime('23:30', 1), '00:00');
  assert.equal(stepTime('06:00:00', 1), '06:30');
  assert.equal(stepTime('06:00', 1, 15), '06:15');
  assert.equal(stepTime('nope', 1), null);
});

test('each mode is described with its times, and a missing mode counts as Smart', () => {
  assert.match(describeOvernight('Eco', '00:00:00', '06:00:00'), /From 00:00 to 06:00 .*Eco/);
  assert.match(describeOvernight('Off', '23:00', '05:30'), /turned off/);
  assert.match(describeOvernight('Smart', '00:00', '06:00'), /all night/);
  assert.equal(modeOf('Eco'), 'Eco');
  assert.equal(modeOf('unavailable'), 'Smart');
  assert.deepEqual(OVERNIGHT_MODES.map((m) => m.id), ['Off', 'Eco', 'Smart']);
});
