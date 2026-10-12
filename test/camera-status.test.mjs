import test from 'node:test';
import assert from 'node:assert/strict';
import { signalBars, signalIcon, batteryIcon, ringing, tileAspect, loadView, saveView } from '../src/camera-status.js';

test('signal bars from dBm and category', () => {
  assert.equal(signalBars('-50'), 4);
  assert.equal(signalBars('-60'), 3);
  assert.equal(signalBars('-70'), 2);
  assert.equal(signalBars('-85'), 1);
  assert.equal(signalBars('unavailable', 'good'), 3);
  assert.equal(signalBars('unknown'), -1);
  assert.equal(signalIcon(-1), 'mdi:wifi-strength-off-outline');
  assert.equal(signalIcon(3), 'mdi:wifi-strength-3');
});
test('battery icon steps', () => {
  assert.equal(batteryIcon(100), 'mdi:battery');
  assert.equal(batteryIcon(53), 'mdi:battery-50');
  assert.equal(batteryIcon(3), 'mdi:battery-outline');
  assert.equal(batteryIcon(NaN), 'mdi:battery-unknown');
});
test('ringing', () => {
  const now = Date.parse('2026-10-11T12:00:30Z');
  assert.equal(ringing('on', 'x', now), true);
  assert.equal(ringing('off', '2026-10-11T12:00:00Z', now), true);
  assert.equal(ringing('off', '2026-10-11T11:00:00Z', now), false);
  assert.equal(ringing('off', '2026-10-11T12:00:00Z', now, now - 1000), false);
  assert.equal(ringing('off', 'unknown', now), false);
});
test('tile aspect is clamped', () => {
  assert.equal(tileAspect(0, 0), 16 / 9);
  assert.equal(tileAspect(1920, 1080), 16 / 9);
  assert.equal(tileAspect(1000, 1000), 1);
  assert.equal(tileAspect(500, 1500), 3 / 4);
});
test('remembered view', () => {
  const m = new Map();
  const store = { getItem: (k) => m.get(k) ?? null, setItem: (k, v) => m.set(k, v) };
  assert.equal(loadView('camera.a', store), 'normal');
  saveView('camera.a', 'expanded', store);
  assert.equal(loadView('camera.a', store), 'expanded');
  assert.equal(loadView('camera.b', store), 'normal');
  saveView('camera.a', 'full', store);
  assert.equal(loadView('camera.a', store), 'normal');
});
