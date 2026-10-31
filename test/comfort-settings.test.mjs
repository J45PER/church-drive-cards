import test from 'node:test';
import assert from 'node:assert/strict';
import { zoneControl, zoneStatusText, stepRange, pauseText, stepPause } from '../src/comfort-settings.js';

const states = (extra = {}) => ({
  'input_boolean.clive_climate_downstairs_auto': { entity_id: 'input_boolean.clive_climate_downstairs_auto', state: 'on' },
  'input_number.clive_climate_downstairs_low': { state: '18.0' },
  'input_number.clive_climate_downstairs_high': { state: '21.0' },
  'input_datetime.clive_climate_downstairs_resume': { state: '2000-01-01 00:00:00', attributes: { timestamp: 946684800 } },
  'input_text.clive_climate_downstairs_note': { state: '08:22: Downstairs was set by hand (off). Paused until 10:22.' },
  ...extra,
});

test('a thermostat with a Clive switch has its range and note', () => {
  const z = zoneControl(states(), 'climate.downstairs');
  assert.equal(z.controlled, true);
  assert.equal(z.low, 18);
  assert.equal(z.high, 21);
  assert.equal(z.pausedUntil, null);
  assert.match(z.note, /set by hand/);
});

test('a thermostat with no switch (or an unavailable one) is not Clive’s', () => {
  assert.equal(zoneControl(states(), 'climate.hall'), null);
  assert.equal(zoneControl(states({ 'input_boolean.clive_climate_downstairs_auto': { state: 'unavailable' } }), 'climate.downstairs'), null);
});

test('a pause in the future shows, a pause in the past or the year 2000 does not', () => {
  const future = Date.now() / 1000 + 3600;
  const z = zoneControl(states({ 'input_datetime.clive_climate_downstairs_resume': { state: 'x', attributes: { timestamp: future } } }), 'climate.downstairs');
  assert.ok(z.pausedUntil > Date.now());
  assert.match(zoneStatusText(z, 'Clive'), /^Clive controlled · paused until \d\d:\d\d$/);
  const past = zoneControl(states({ 'input_datetime.clive_climate_downstairs_resume': { state: 'x', attributes: { timestamp: Date.now() / 1000 - 60 } } }), 'climate.downstairs');
  assert.equal(past.pausedUntil, null);
});

test('status words', () => {
  assert.equal(zoneStatusText(zoneControl(states(), 'climate.downstairs'), 'Clive'), 'Clive controlled');
  const manual = zoneControl(states({ 'input_boolean.clive_climate_downstairs_auto': { state: 'off' } }), 'climate.downstairs');
  assert.equal(zoneStatusText(manual, 'Clive'), 'Manual');
});

test('the range ends move by half a degree and keep a degree apart', () => {
  assert.equal(stepRange('low', 18, 21, 0.5), 18.5);
  assert.equal(stepRange('low', 19.5, 21, 0.5), 20);
  assert.equal(stepRange('low', 20, 21, 0.5), 20);
  assert.equal(stepRange('high', 21, 18, -0.5), 20.5);
  assert.equal(stepRange('high', 19, 18, -0.5), 19);
});

test('the pause is read as words and steps by a quarter of an hour within the helper limits', () => {
  assert.equal(pauseText(30), '30 min');
  assert.equal(pauseText(60), '1 h');
  assert.equal(pauseText(90), '1 h 30 min');
  assert.equal(pauseText(null), '–');
  assert.equal(stepPause(30, 1), 45);
  assert.equal(stepPause(30, -1), 15);
  assert.equal(stepPause(15, -1), 15);
  assert.equal(stepPause(720, 1), 720);
});

test('a thermostat reports the house pause and its limits when the helper exists', () => {
  const withPause = states({ 'input_number.clive_climate_pause_minutes': { state: '30.0', attributes: { min: 15, max: 720, step: 15 } } });
  const z = zoneControl(withPause, 'climate.downstairs');
  assert.equal(z.pauseMin, 30);
  assert.deepEqual(z.pauseLimits, { min: 15, max: 720, step: 15 });
  assert.equal(zoneControl(states(), 'climate.downstairs').pauseMin, null);
});
