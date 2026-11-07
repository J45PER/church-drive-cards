import test from 'node:test';
import assert from 'node:assert/strict';
import { daysText, summary, toggleDay, newSchedule, problem, overlaps, validTime } from '../src/purifier-schedule-logic.js';

test('days read like a person would say them', () => {
  assert.equal(daysText([0, 1, 2, 3, 4]), 'Mon-Fri');
  assert.equal(daysText([5, 6]), 'Sat & Sun');
  assert.equal(daysText([0, 3, 5]), 'Mon, Thu & Sat');
  assert.equal(daysText([0, 1, 2, 3, 4, 5, 6]), 'Every day');
  assert.equal(daysText([2]), 'Wed');
  assert.equal(daysText([0, 1, 2, 4]), 'Mon-Wed & Fri');
  assert.equal(daysText([]), 'No days');
  assert.equal(daysText([4, 4, 9, -1]), 'Fri');
});

test('summary matches the integration', () => {
  assert.equal(summary({ days: [0, 1, 2, 3, 4], auto: '07:00', sleep: '23:30' }), 'Mon-Fri Auto: 07:00-23:30');
  assert.equal(summary({ days: [0, 3, 5], auto: '08:00', sleep: '22:00' }), 'Mon, Thu & Sat Auto: 08:00-22:00');
});

test('toggling a day keeps the days in order', () => {
  const s = { id: 'a', days: [0, 4], auto: '07:00', sleep: '22:00' };
  assert.deepEqual(toggleDay(s, 2).days, [0, 2, 4]);
  assert.deepEqual(toggleDay(s, 4).days, [0]);
  assert.deepEqual(s.days, [0, 4]);
});

test('a new schedule takes the days nobody has', () => {
  assert.deepEqual(newSchedule([]).days, [0, 1, 2, 3, 4]);
  assert.deepEqual(newSchedule([{ id: 's1', days: [0, 1, 2, 3, 4], auto: '07:00', sleep: '22:00' }]).days, [5, 6]);
  const full = [{ id: 's1', days: [0, 1, 2, 3, 4, 5, 6], auto: '07:00', sleep: '22:00' }];
  assert.deepEqual(newSchedule(full).days, [0, 1, 2, 3, 4]);
  assert.equal(newSchedule(full).id, 's2');
});

test('problems stop a save', () => {
  const ok = { id: 'a', days: [1], auto: '07:00', sleep: '22:00' };
  assert.equal(problem([ok]), '');
  assert.match(problem([{ ...ok, days: [] }]), /day/);
  assert.match(problem([{ ...ok, sleep: '' }]), /times/);
  assert.equal(problem([]), '');
  assert.ok(validTime('00:00') && validTime('23:59') && !validTime('24:00') && !validTime('7:00'));
});

test('overlapping days are named', () => {
  assert.equal(overlaps([{ days: [0, 1] }, { days: [1, 2] }]), 'Tue');
  assert.equal(overlaps([{ days: [0] }, { days: [1] }]), '');
});
