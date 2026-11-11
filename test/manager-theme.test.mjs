import test from 'node:test';
import assert from 'node:assert/strict';
import { KEY, effective, initTheme, isTheme, opposite, readStored, writeStored } from '../web/manager/theme.js';

const store = (initial) => { const m = new Map(initial ? [[KEY, initial]] : []); return { getItem: (k) => (m.has(k) ? m.get(k) : null), setItem: (k, v) => m.set(k, v), m }; };
const doc = () => ({ documentElement: { dataset: {} } });

test('only light and dark are themes', () => {
  assert.equal(isTheme('dark'), true); assert.equal(isTheme('light'), true);
  for (const v of [null, undefined, '', 'auto', 'Dark', 1]) assert.equal(isTheme(v), false);
});

test('the choice wins; with none, the device decides', () => {
  assert.equal(effective('light', true), 'light');
  assert.equal(effective('dark', false), 'dark');
  assert.equal(effective(null, true), 'dark');
  assert.equal(effective(null, false), 'light');
  assert.equal(effective('rubbish', true), 'dark');
  assert.equal(opposite('dark'), 'light'); assert.equal(opposite('light'), 'dark');
});

test('a stored choice is read back and a damaged one is ignored', () => {
  const s = store(); writeStored('dark', s); assert.equal(readStored(s), 'dark');
  assert.equal(readStored(store('purple')), null);
  assert.equal(readStored({ getItem() { throw new Error('blocked'); } }), null);
  assert.doesNotThrow(() => writeStored('light', { setItem() { throw new Error('blocked'); } }));
});

test('a stored choice goes on the page before it is drawn; no choice leaves the page following the device', () => {
  const d = doc(); assert.equal(initTheme(d, store('dark')), 'dark'); assert.equal(d.documentElement.dataset.theme, 'dark');
  const e = doc(); assert.equal(initTheme(e, store()), null); assert.equal(e.documentElement.dataset.theme, undefined);
});
