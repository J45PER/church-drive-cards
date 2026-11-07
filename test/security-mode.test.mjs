import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isSecurityRow, isSecurityLight, watchValue } from '../src/security-mode.js';

const mk = (active, extra = {}) => ({
  states: {
    'input_boolean.clive_away_lights_active': { state: active ? 'on' : 'off', attributes: {} },
    'light.tv_lightstrip': { state: 'on', attributes: { brightness: 100 } },
    'light.kitchen': { state: 'on', attributes: { brightness: 100 } },
    'light.living_room': { state: 'on', attributes: { entity_id: ['light.tv_lightstrip', 'light.lamp'] } },
    ...extra,
  },
  entities: { 'light.tv_lightstrip': { labels: ['away_tv_simulation'] }, 'light.kitchen': { labels: [] } },
});

test('labelled light and its group are security rows only while active', () => {
  assert.ok(isSecurityLight(mk(true), 'light.tv_lightstrip'));
  assert.ok(isSecurityRow(mk(true), 'light.living_room'));
  assert.ok(!isSecurityRow(mk(true), 'light.kitchen'));
  assert.ok(!isSecurityRow(mk(false), 'light.tv_lightstrip'));
});

test('flicker (attribute change) does not change the watch value in security mode', () => {
  const a = mk(true);
  const b = mk(true, { 'light.tv_lightstrip': { state: 'on', attributes: { brightness: 230 } } });
  assert.equal(watchValue(a, 'light.tv_lightstrip'), watchValue(b, 'light.tv_lightstrip'));
  b.states['light.tv_lightstrip'] = { state: 'off', attributes: {} };
  assert.notEqual(watchValue(a, 'light.tv_lightstrip'), watchValue(b, 'light.tv_lightstrip'));
});

test('normal lights still compare by state object', () => {
  const h = mk(true);
  assert.equal(watchValue(h, 'light.kitchen'), h.states['light.kitchen']);
});
