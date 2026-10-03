// Run with: npm test
import { test, mock } from 'node:test';
import assert from 'node:assert/strict';

globalThis.window = new EventTarget();
const { loadIcons, iconFor, iconSlots, iconLabel, resetIconsForTest } = await import('../src/icon-library.js');

const LIST = {
  icons: { fan: { sleep: 'mdi:moon-waning-crescent', 'speed_low': 'mdi:speedometer-slow' }, charger: { 'eco+': 'mdi:solar-power' } },
  defaults: { fan: { sleep: 'mdi:power-sleep' } },
  groups: { fan: 'Fan' },
};

test('until the list arrives, a card keeps its own icon', () => {
  resetIconsForTest();
  assert.equal(iconFor('fan', 'sleep', 'mdi:power-sleep'), 'mdi:power-sleep');
});

test("Home Assistant's icon takes over, whatever the case of the mode", async () => {
  resetIconsForTest();
  const hass = { callWS: async () => LIST };
  await loadIcons(hass);
  assert.equal(iconFor('fan', 'sleep', 'mdi:power-sleep'), 'mdi:moon-waning-crescent');
  assert.equal(iconFor('fan', 'Sleep', 'mdi:power-sleep'), 'mdi:moon-waning-crescent');
  assert.equal(iconFor('charger', 'Eco+', 'x'), 'mdi:solar-power');
  assert.equal(iconFor('fan', 'turbo', 'mdi:rocket-launch'), 'mdi:rocket-launch');
  assert.equal(iconFor('nothing', 'sleep', 'mdi:fan'), 'mdi:fan');
  assert.equal(iconSlots().groups.fan, 'Fan');
});

test('without the integration the built-in icons stay, and it tries again later, not every update', async () => {
  resetIconsForTest();
  mock.timers.enable({ apis: ['Date'], now: 100000 });
  let calls = 0;
  const hass = {
    callWS: async () => {
      calls += 1;
      throw new Error('unknown command');
    },
  };
  await loadIcons(hass);
  assert.equal(calls, 1);
  loadIcons(hass);
  assert.equal(calls, 1);
  mock.timers.setTime(100000 + 16000);
  await loadIcons(hass);
  assert.equal(calls, 2);
  assert.equal(iconFor('fan', 'sleep', 'mdi:power-sleep'), 'mdi:power-sleep');
  mock.timers.reset();
});

test('modes are named nicely in the editor', () => {
  assert.equal(iconLabel('speed_low'), 'Speed low');
  assert.equal(iconLabel('eco+'), 'Eco+');
});
