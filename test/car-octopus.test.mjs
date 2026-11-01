import test from 'node:test';
import assert from 'node:assert/strict';
import { octopusCar } from '../src/car-octopus.js';

const E = { dispatch: 'binary_sensor.id5_dispatching' };
const hass = (over = {}) => ({
  states: {
    'binary_sensor.id5_dispatching': { state: 'on', attributes: { current_end: '2026-11-01T05:30:00+00:00' } },
    'sensor.zappi_plug_status': { state: 'Charging', attributes: {} },
    'sensor.zappi_charging_power': { state: '7000', attributes: {} },
    'input_select.zappi_car_role': { state: 'Primary', attributes: {} },
    ...over,
  },
});

test('the Primary car on a charging Zappi is plugged in and charging, with Octopus plan', () => {
  assert.deepEqual(octopusCar(hass(), E), { plugged: true, charging: true, plan: 'Octopus charging until 05:30' });
});

test('an Additional or unanswered car on the Zappi is not this car', () => {
  assert.equal(octopusCar(hass({ 'input_select.zappi_car_role': { state: 'Additional', attributes: {} } }), E).plugged, false);
  assert.equal(octopusCar(hass({ 'input_select.zappi_car_role': { state: 'Unknown', attributes: {} } }), E).charging, false);
});

test('an unplugged Zappi means the car is not plugged in, and the next charge is said', () => {
  const h = hass({
    'sensor.zappi_plug_status': { state: 'EV Disconnected', attributes: {} },
    'binary_sensor.id5_dispatching': { state: 'off', attributes: { next_start: '2026-11-01T02:30:00+00:00', next_end: '2026-11-01T04:30:00+00:00' } },
  });
  assert.deepEqual(octopusCar(h, E), { plugged: false, charging: false, plan: 'Next Octopus charge 02:30 to 04:30' });
});

test('a car that is not made by Octopus is left alone', () => {
  assert.equal(octopusCar(hass(), { dispatch: '' }), null);
  assert.equal(octopusCar(hass(), {}), null);
});

import { octopusPlan, planLine } from '../src/car-octopus.js';

const NOW = Date.parse('2026-11-01T00:30:00+00:00');
const planHass = (attrs, state = 'off') => ({ states: { 'binary_sensor.octopus_energy_x_intelligent_dispatching': { state, attributes: attrs } } });

test('the plan lists unfinished dispatches, oldest first, with positive kWh', () => {
  const h = planHass({
    planned_dispatches: [
      { start: '2026-11-01T02:30:00+00:00', end: '2026-11-01T04:30:00+00:00', charge_in_kwh: -15.556, source: 'SMART' },
      { start: '2026-10-31T23:00:00+00:00', end: '2026-10-31T23:30:00+00:00', charge_in_kwh: -3.889, source: 'SMART' },
      { start: '2026-11-01T00:00:00+00:00', end: '2026-11-01T01:00:00+00:00', charge_in_kwh: -2, source: 'BOOST' },
    ],
  }, 'on');
  const p = octopusPlan(h, NOW);
  assert.equal(p.active, true);
  assert.deepEqual(p.slots.map((x) => [x.source, x.kwh]), [['BOOST', 2], ['SMART', 15.556]]);
});

test('a plan line says now, the times, the kWh and Smart or Boost', () => {
  const x = { start: Date.parse('2026-11-01T00:00:00+00:00'), end: Date.parse('2026-11-01T01:00:00+00:00'), kwh: 2, source: 'BOOST' };
  assert.equal(planLine(x, NOW), 'Now: 00:00 to 01:00 · 2.0 kWh · Boost');
  assert.equal(planLine({ ...x, start: NOW + 3600e3, end: NOW + 7200e3, kwh: 0, source: 'SMART' }, NOW), '01:30 to 02:30 · Smart');
});

test('no Octopus device means no plan; no dispatches means an empty one', () => {
  assert.equal(octopusPlan({ states: {} }, NOW), null);
  assert.deepEqual(octopusPlan(planHass({}), NOW), { slots: [], active: false });
});
