import test from 'node:test';
import assert from 'node:assert/strict';
import { weekStatIds, lastDays, buildWeek, dayCheapShare, lagNote, balance } from '../src/energy-week.js';

const m = { elec: 'octopus_energy_electricity_24l3478610_1160001231179_', gas: 'octopus_energy_gas_e6s16825432562_7586596805_' };
const ids = weekStatIds(m);
// Day buckets as Home Assistant returns them (start of the local day, in ms), BST: 1 Oct 2026 is 1790809200000.
const day = (n) => 1790809200000 + (n - 1) * 86400000; // n = day of October
const NOW = new Date('2026-10-11T14:30:00+01:00').getTime();
const TZ = 'Europe/London';

test('finds the statistic ids from the meters', () => {
  assert.equal(ids.elecCost, 'octopus_energy:electricity_24l3478610_1160001231179_previous_accumulative_cost');
  assert.equal(ids.gasKwh, 'octopus_energy:gas_e6s16825432562_7586596805_previous_accumulative_consumption_kwh');
  assert.equal(ids.offPeak, 'octopus_energy:electricity_24l3478610_1160001231179_previous_accumulative_consumption_off_peak');
  assert.deepEqual(weekStatIds({ elec: null, gas: m.gas }).elecCost, undefined);
});

test('the seven days end yesterday, oldest first', () => {
  assert.deepEqual(lastDays(NOW, 7, TZ), ['2026-10-04', '2026-10-05', '2026-10-06', '2026-10-07', '2026-10-08', '2026-10-09', '2026-10-10']);
});

test('electricity behind gas shows gaps, and says how far', () => {
  const result = {
    [ids.elecCost]: [4, 5, 6, 7].map((d, i) => ({ start: day(d), change: [1.64, 4.83, 2.54, 2.17][i] })),
    [ids.elecKwh]: [4, 5, 6, 7].map((d, i) => ({ start: day(d), change: [7.4, 44.2, 12.2, 7.9][i] })),
    [ids.gasCost]: [4, 5, 6, 7, 8, 9].map((d) => ({ start: day(d), change: 0.3 })),
    [ids.gasKwh]: [4, 5, 6, 7, 8, 9].map((d) => ({ start: day(d), change: 5.5 })),
  };
  const w = buildWeek(result, ids, NOW, 7, TZ);
  assert.equal(w.days.length, 7);
  assert.equal(w.days[3].elecCost, 2.17); // Wed 7 Oct
  assert.equal(w.days[4].elecCost, null); // Thu 8 Oct: Octopus hasn't sent electricity
  assert.equal(w.days[4].gasCost, 0.3);
  assert.equal(w.days[6].gasCost, null); // Sat 10 Oct: nothing yet
  assert.equal(w.elecLatest, '2026-10-07');
  assert.equal(w.elecBehind, 3);
  assert.equal(w.gasBehind, 1);
  assert.equal(Math.round(w.elecCost * 100) / 100, 11.18);
  assert.equal(Math.round(w.gasCost * 100) / 100, 1.8);
  assert.equal(w.elecDays, 4);
  const note = lagNote(w, (d) => d);
  assert.match(note, /Electricity is 3 days behind/);
  assert.match(note, /Gas is 1 day behind/);
});

test('a missing meter is not reported as behind', () => {
  const only = weekStatIds({ elec: m.elec, gas: null });
  const w = buildWeek({ [only.elecCost]: [{ start: day(10), change: 2 }] }, only, NOW, 7, TZ);
  assert.equal(lagNote(w, (d) => d), '');
  assert.equal(w.hasGas, false);
  assert.equal(w.latestWithData, '2026-10-10');
});

test('cheap share comes from the off-peak and other rates', () => {
  const result = {
    [ids.elecCost]: [{ start: day(10), change: 2 }],
    [ids.offPeak]: [{ start: day(10), change: 6 }],
    [ids.peak]: [{ start: day(10), change: 4 }],
  };
  const w = buildWeek(result, ids, NOW, 7, TZ);
  assert.equal(w.cheapShare, 60);
  assert.equal(dayCheapShare(w.days[6]), 60);
  assert.equal(dayCheapShare({ offPeak: null, peak: 3, standard: null }), null);
});

test('nothing at all gives no average', () => {
  const w = buildWeek({}, ids, NOW, 7, TZ);
  assert.equal(w.average, null);
  assert.equal(w.cost, 0);
});

test('the balance splits the spending between electricity and gas', () => {
  const w = buildWeek({ [ids.elecCost]: [{ start: day(9), change: 3 }], [ids.gasCost]: [{ start: day(9), change: 1 }] }, ids, NOW, 7, TZ);
  assert.deepEqual(balance(w), { elec: 75, gas: 25 });
  assert.equal(balance(buildWeek({}, ids, NOW, 7, TZ)), null);
});

test('days follow the Home Assistant time zone, not the viewer\'s', () => {
  // The same bucket (midnight 7 Oct in London) is still the 7th when read from a UTC browser, because the zone is given.
  const w = buildWeek({ [ids.elecCost]: [{ start: day(7), change: 2.17 }] }, ids, NOW, 7, 'Europe/London');
  assert.equal(w.days.find((d) => d.date === '2026-10-07').elecCost, 2.17);
});
