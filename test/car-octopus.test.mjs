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
