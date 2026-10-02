// Run with: npm test
import { test, mock } from 'node:test';
import assert from 'node:assert/strict';

globalThis.window = new EventTarget();
const { loadUniversalScenes, universalScenes } = await import('../src/universal-scenes.js');

test('a library that fails to load (HA still starting) is tried again later, not never', async () => {
  mock.timers.enable({ apis: ['Date'], now: 1000 });
  let calls = 0;
  const hass = {
    callWS: async () => {
      calls += 1;
      if (calls === 1) throw new Error('unknown command');
      return { scenes: [{ key: 'bright', name: 'Bright' }] };
    },
  };

  await loadUniversalScenes(hass);
  assert.equal(calls, 1);
  assert.equal(universalScenes().length, 0);

  // Every hass update calls this; straight after a failure it waits rather than hammering HA.
  loadUniversalScenes(hass);
  assert.equal(calls, 1);

  mock.timers.tick(5000);
  await loadUniversalScenes(hass);
  assert.equal(calls, 2);
  assert.equal(universalScenes().length, 1);

  // Once loaded it isn't fetched again.
  mock.timers.tick(60000);
  loadUniversalScenes(hass);
  assert.equal(calls, 2);
  mock.timers.reset();
});
