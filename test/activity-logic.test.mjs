import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultFilters, rangeFor, windowFor, buildQuery, toggleKind, dayHeading, groupByDay, lineParts, kindLabel, KINDS, parseDay, dayText, summary } from '../src/activity-logic.js';

// Tests run with TZ=Europe/London (npm test); Sat 24 Oct 2026 is still BST and Sun 25th is when the clocks go back.
const NOW = new Date(2026, 9, 26, 15, 30); // Mon 26 Oct 2026, 15:30
const local = (d, h = 0, m = 0) => new Date(2026, 9, d, h, m).getTime() / 1000;
const f = (over) => ({ ...defaultFilters(), ...over });

test('today and yesterday are whole local days', () => {
  assert.deepEqual(rangeFor(f({ day: 'today' }), NOW), { since: local(26), until: local(27) });
  assert.deepEqual(rangeFor(f({ day: 'yesterday' }), NOW), { since: local(25), until: local(26) });
});

test('the last 7 and 30 days include today', () => {
  assert.deepEqual(rangeFor(f({ day: 'week' }), NOW), { since: local(20), until: local(27) });
  assert.equal(rangeFor(f({ day: 'month' }), NOW).since, new Date(2026, 8, 27).getTime() / 1000);
});

test('a day that has 25 hours (the clocks going back) is still one day', () => {
  const r = rangeFor(f({ day: 'yesterday' }), NOW);
  assert.equal((r.until - r.since) / 3600, 25);
});

test('a custom range covers whole days, either end can be open, and reversed dates are put right', () => {
  assert.deepEqual(rangeFor(f({ day: 'custom', from: '2026-10-22', to: '2026-10-23' }), NOW), { since: local(22), until: local(24) });
  assert.deepEqual(rangeFor(f({ day: 'custom', from: '2026-10-22' }), NOW), { since: local(22) });
  assert.deepEqual(rangeFor(f({ day: 'custom', to: '2026-10-22' }), NOW), { until: local(23) });
  assert.deepEqual(rangeFor(f({ day: 'custom', from: '2026-10-23', to: '2026-10-22' }), NOW), { since: local(22), until: local(24) });
  assert.deepEqual(rangeFor(f({ day: 'custom', from: 'nonsense', to: '2026-02-31' }), NOW), {});
});

test('day text round-trips and rejects impossible dates', () => {
  assert.equal(dayText(parseDay('2026-03-09')), '2026-03-09');
  assert.equal(parseDay('2026-02-31'), null);
  assert.equal(parseDay(''), null);
});

test('time of day: presets, a custom window, and bad times ignored', () => {
  assert.deepEqual(windowFor(f({ time: 'all' })), { start: '', end: '' });
  assert.deepEqual(windowFor(f({ time: 'night' })), { start: '22:00', end: '06:00' });
  assert.deepEqual(windowFor(f({ time: 'custom', start: '07:15', end: '25:00' })), { start: '07:15', end: '' });
});

test('the question for the house carries only what was chosen', () => {
  assert.deepEqual(buildQuery(f({}), NOW), { type: 'church_drive/activity', since: local(26), until: local(27), limit: 100 });
  const q = buildQuery(f({ day: 'week', time: 'evening', kinds: ['clive', 'presence'], who: 'Hayley' }), NOW, 50, 100);
  assert.deepEqual(q, { type: 'church_drive/activity', since: local(20), until: local(27), limit: 50, start: '18:00', end: '22:00', kinds: ['clive', 'presence'], who: 'Hayley', offset: 100 });
});

test('kinds toggle on and off without changing the original', () => {
  const a = f({});
  const b = toggleKind(a, 'clive');
  assert.deepEqual(b.kinds, ['clive']);
  assert.deepEqual(a.kinds, []);
  assert.deepEqual(toggleKind(b, 'clive').kinds, []);
});

test('day headings', () => {
  assert.equal(dayHeading(local(26, 9), NOW), 'Today');
  assert.equal(dayHeading(local(25, 23, 59), NOW), 'Yesterday');
  assert.equal(dayHeading(local(24, 8), NOW), 'Sat 24 Oct');
  assert.match(dayHeading(new Date(2025, 11, 31, 9).getTime() / 1000, NOW), /2025/);
});

test('lines are grouped under their day in order', () => {
  const lines = [{ ts: local(26, 12) }, { ts: local(26, 9) }, { ts: local(25, 22) }, { ts: local(26, 1) }];
  const g = groupByDay(lines, NOW);
  assert.deepEqual(g.map((x) => [x.heading, x.entries.length]), [['Today', 2], ['Yesterday', 1], ['Today', 1]]);
});

test('the assistant is named by the house; a line says who asked him, or which automation ran him', () => {
  assert.deepEqual(lineParts({ kind: 'clive', who: '', text: 'turned off Downstairs', by: 'Hayley' }, 'Gregg'), { who: 'Gregg', text: 'turned off Downstairs', note: 'asked by Hayley' });
  assert.deepEqual(lineParts({ kind: 'clive', who: '', text: 'x' }), { who: 'Clive', text: 'x', note: 'asked' });
  assert.deepEqual(lineParts({ kind: 'clive_auto', who: '', text: 'x', rule: 'keep the climate comfortable' }), { who: 'Clive', text: 'x', note: 'automatic: keep the climate comfortable' });
  assert.deepEqual(lineParts({ kind: 'clive_auto', who: '', text: 'x' }).note, 'automatic');
  const label = (id, name) => kindLabel(KINDS.find((k) => k.id === id), name);
  assert.deepEqual([label('clive', 'Gregg'), label('clive_auto'), label('user'), label('presence')], ['Gregg asked', 'Clive automatic', 'People', 'Coming and going']);
  assert.deepEqual(lineParts({ kind: 'user', who: '', text: 'x' }).who, 'Someone');
});

test('summary', () => {
  assert.equal(summary(f({}), 1), '1 line');
  assert.equal(summary(f({ who: 'Ian' }), 3), '3 lines match');
});
