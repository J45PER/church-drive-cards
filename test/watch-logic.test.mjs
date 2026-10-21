import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultConfig, normalise, setPageOn, movePage, setItemShown, isHidden, setCar, setLightsTop, setCliveFlag, parseLights, carCandidates, climateDevices } from '../src/watch-logic.js';

const ids = (cfg) => cfg.pages.map((p) => p.id);

test('every page is on by default and the settings survive being normalised', () => {
  const cfg = defaultConfig();
  assert.deepEqual(ids(cfg), ['alarm', 'climate', 'lights', 'cleaning', 'car']);
  assert.ok(cfg.pages.every((p) => p.on));
  assert.deepEqual(normalise(cfg), cfg);
  assert.deepEqual(normalise(null), cfg);
});

test('normalise keeps order, drops unknown pages and adds missing ones at the end', () => {
  const cfg = normalise({ pages: [{ id: 'car', on: true }, { id: 'tea' }, { id: 'alarm', on: false }, 'car'] });
  assert.deepEqual(ids(cfg), ['car', 'alarm', 'climate', 'lights', 'cleaning']);
  assert.equal(cfg.pages[1].on, false);
});

test('pages can be turned off and moved, and stay put at the ends', () => {
  let cfg = defaultConfig();
  cfg = setPageOn(cfg, 'lights', false);
  assert.equal(cfg.pages.find((p) => p.id === 'lights').on, false);
  cfg = movePage(cfg, 'lights', -1);
  assert.deepEqual(ids(cfg), ['alarm', 'lights', 'climate', 'cleaning', 'car']);
  assert.equal(movePage(cfg, 'alarm', -1), cfg);
  assert.equal(movePage(cfg, 'car', 1), cfg);
});

test('items are hidden and shown again, and an empty list leaves no trace', () => {
  let cfg = defaultConfig();
  cfg = setItemShown(cfg, 'climate', 'fan.bedroom', false);
  assert.ok(isHidden(cfg, 'climate', 'fan.bedroom'));
  assert.deepEqual(cfg.hidden, { climate: ['fan.bedroom'] });
  cfg = setItemShown(cfg, 'climate', 'fan.bedroom', true);
  assert.deepEqual(cfg.hidden, {});
});

test('cars, the lights top level and Clive toggles', () => {
  let cfg = defaultConfig();
  const car = { name: 'Astra', battery: 'sensor.astra_battery', plugged: 'binary_sensor.astra_battery_plugged' };
  cfg = setCar(cfg, car, true);
  cfg = setCar(cfg, car, true);
  assert.equal(cfg.cars.length, 1);
  assert.equal(setCar(cfg, car, false).cars.length, 0);
  assert.equal(setLightsTop(cfg, 'zones').lights_top, 'zones');
  assert.equal(setLightsTop(cfg, 'attic').lights_top, 'rooms');
  assert.equal(setCliveFlag(cfg, 'follow', true).clive.follow, true);
});

const DASH = {
  views: [
    { path: 'home', sections: [] },
    {
      path: 'lighting',
      sections: [
        {
          cards: [
            {
              type: 'custom:section-panel-card',
              title: 'Ground Floor',
              cards: [
                { type: 'custom:light-control-card', area: 'kitchen', entities: [{ entity: 'light.kitchen' }, { entity: 'light.kitchen_spotlights', level: '1' }, { entity: 'light.kitchen_spot_1', level: 2 }] },
                { type: 'custom:light-control-card', entities: [{ entity: 'light.entrance', name: 'Front hall' }] },
              ],
            },
          ],
        },
      ],
    },
  ],
};

test('rooms and zones are read from the Lighting view, leaving out the lights inside a zone', () => {
  const floors = parseLights(DASH, (id) => ({ 'light.kitchen': 'Kitchen', 'light.kitchen_spotlights': 'Kitchen Spotlights' })[id] || '');
  assert.equal(floors.length, 1);
  assert.equal(floors[0].name, 'Ground Floor');
  const [kitchen, hall] = floors[0].rooms;
  assert.equal(kitchen.head, 'light.kitchen');
  assert.equal(kitchen.title, 'Kitchen');
  assert.deepEqual(kitchen.zones.map((z) => z.entity), []); // level "1" is a string here: only a level of exactly 1 counts
  assert.equal(hall.title, 'Front hall');
});

test('a level of 1 (a number or missing) is a zone; deeper is not', () => {
  const dash = { views: [{ path: 'lighting', cards: [{ type: 'custom:section-panel-card', title: 'F', cards: [{ type: 'custom:light-control-card', entities: [{ entity: 'light.r' }, { entity: 'light.z1', level: 1 }, { entity: 'light.z2' }, { entity: 'light.deep', level: 2 }] }] }] }] };
  const [floor] = parseLights(dash);
  assert.deepEqual(floor.rooms[0].zones.map((z) => z.entity), ['light.z1', 'light.z2']);
});

test('without a Lighting view the Lights panel on the Home view is one floor', () => {
  const dash = { views: [{ path: 'home', cards: [{ type: 'custom:section-panel-card', title: 'Lights', cards: [{ type: 'custom:light-control-card', entities: [{ entity: 'light.kitchen' }] }] }] }] };
  const floors = parseLights(dash);
  assert.equal(floors[0].name, 'Home');
  assert.equal(floors[0].rooms[0].head, 'light.kitchen');
  assert.deepEqual(parseLights({}), []);
});

test('cars are found from a plugged-in sensor with its battery', () => {
  const states = {
    'binary_sensor.astra_battery_plugged': { attributes: {} },
    'sensor.astra_battery': { attributes: { friendly_name: 'Vauxhall Astra Battery' } },
    'binary_sensor.lonely_battery_plugged': { attributes: {} },
  };
  assert.deepEqual(carCandidates(states), [{ name: 'Vauxhall Astra', battery: 'sensor.astra_battery', plugged: 'binary_sensor.astra_battery_plugged' }]);
});

test('climate devices are thermostats, fans and purifiers', () => {
  const states = { 'climate.down': { attributes: { friendly_name: 'Downstairs' } }, 'fan.air_purifier': { attributes: { friendly_name: 'Air Purifier ' } }, 'fan.bed': { attributes: {} }, 'light.x': { attributes: {} } };
  const list = climateDevices(states);
  assert.deepEqual(list.map((d) => [d.id, d.kind]), [['fan.air_purifier', 'Air purifier'], ['fan.bed', 'Fan'], ['climate.down', 'Thermostat or air con']].sort((a, b) => a[1].localeCompare(b[1])));
});
