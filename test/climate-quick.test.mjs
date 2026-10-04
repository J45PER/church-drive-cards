import test from 'node:test';
import assert from 'node:assert/strict';
import { quickPlan, ccOnMode } from '../src/climate-quick.js';

const attrs = { hvac_modes: ['off', 'heat'], preset_modes: ['none', 'eco'], preset_mode: 'none' };

test('Eco on a thermostat that is off switches it on first', () => {
  assert.deepEqual(quickPlan('off', attrs, { name: 'Eco', preset_mode: 'eco' }), [
    { service: 'set_hvac_mode', data: { hvac_mode: 'heat' } },
    { service: 'set_preset_mode', data: { preset_mode: 'eco' } },
  ]);
});

test('Eco on a thermostat that is already heating is just the preset', () => {
  assert.deepEqual(quickPlan('heat', attrs, { name: 'Eco', preset_mode: 'eco' }), [{ service: 'set_preset_mode', data: { preset_mode: 'eco' } }]);
});

test('Heat leaves Eco, and Off does not send a preset', () => {
  assert.deepEqual(quickPlan('heat', { ...attrs, preset_mode: 'eco' }, { name: 'Heat', hvac_mode: 'heat' }), [{ service: 'set_preset_mode', data: { preset_mode: 'none' } }]);
  assert.deepEqual(quickPlan('heat', { ...attrs, preset_mode: 'eco' }, { name: 'Off', hvac_mode: 'off' }), [{ service: 'set_hvac_mode', data: { hvac_mode: 'off' } }]);
});

test('a quick setting with its own mode and preset is left as written', () => {
  assert.deepEqual(quickPlan('off', attrs, { name: 'Eco', hvac_mode: 'heat', preset_mode: 'eco' }), [
    { service: 'set_hvac_mode', data: { hvac_mode: 'heat' } },
    { service: 'set_preset_mode', data: { preset_mode: 'eco' } },
  ]);
});

test('the mode that means on', () => {
  assert.equal(ccOnMode(['off', 'cool', 'auto']), 'auto');
  assert.equal(ccOnMode(['off', 'cool']), 'cool');
  assert.equal(ccOnMode(['off']), null);
  assert.equal(ccOnMode(undefined), null);
});

test('a preset that is Eco but the thermostat has no on mode just sends the preset', () => {
  assert.deepEqual(quickPlan('off', { hvac_modes: ['off'] }, { name: 'Eco', preset_mode: 'eco' }), [{ service: 'set_preset_mode', data: { preset_mode: 'eco' } }]);
});
