// Run with: npm test (uses UK time, so the clock changes are covered).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseRepeat, formatRepeat, parseTask, formatTask, nextOccurrence, firstDue, afterDone } from '../src/repeat.js';

const d = (s) => new Date(s); // local time, e.g. '2026-10-01T09:00'
const iso = (x) => {
  const p = (n) => String(n).padStart(2, '0');
  return x && `${x.getFullYear()}-${p(x.getMonth() + 1)}-${p(x.getDate())}T${p(x.getHours())}:${p(x.getMinutes())}`;
};

test('every repeat round-trips through its words', () => {
  for (const s of [
    'Once',
    'Every day 08:00',
    'Every 3 days 08:00',
    'Mon 09:00, Thu 18:30',
    'Every 2 weeks: Mon 09:00, Thu 18:30',
    'Monthly on the 15th 10:00',
    'Every 3 months on the last day 10:00',
    'Monthly on the 1st 07:05',
    'Monthly on the 22nd 07:05',
    'Monthly on the 23rd 07:05',
    'Monthly on the 11th 07:05',
    'Yearly on 12 Mar 09:00',
    'Every 30 days after done 09:00',
    'Every week after done 09:00',
    'Every 2 months after done 09:00',
  ])
    assert.equal(formatRepeat(parseRepeat(s)), s);
});

test('older cleaning words still read', () => {
  assert.deepEqual(parseRepeat('Every day 08:00'), { type: 'days', n: 1, time: '08:00' });
  assert.equal(parseRepeat('Mon 9:00').slots[0][1], '09:00');
});

test('tasks keep notes, and plain descriptions are all notes', () => {
  assert.deepEqual(parseTask('Pick up milk on the way'), { repeat: null, who: 'none', notes: 'Pick up milk on the way' });
  const t = parseTask('Mon 09:00 · for Jamie, Hayley · use the blue mop');
  assert.deepEqual(t.who, ['Jamie', 'Hayley']);
  assert.equal(t.notes, 'use the blue mop');
  assert.equal(formatTask(t.repeat, t.who, t.notes), 'Mon 09:00 · for Jamie, Hayley · use the blue mop');
  assert.equal(formatTask(parseRepeat('Once'), 'everyone', ''), 'Once · for everyone');
  assert.equal(parseTask('Sat 10:00').who, 'none');
});

test('daily and every-n-days', () => {
  const r = parseRepeat('Every 3 days 08:00');
  assert.equal(iso(firstDue(r, d('2026-10-01T00:00'), d('2026-10-01T07:00'))), '2026-10-01T08:00');
  assert.equal(iso(firstDue(r, d('2026-10-01T00:00'), d('2026-10-01T09:00'))), '2026-10-04T08:00');
  assert.equal(iso(afterDone(r, d('2026-10-04T08:00'), d('2026-10-04T12:00'))), '2026-10-07T08:00');
  // ticked off late: stays on its 3-day rhythm
  assert.equal(iso(afterDone(r, d('2026-10-04T08:00'), d('2026-10-09T12:00'))), '2026-10-10T08:00');
});

test('weekly and fortnightly keep their rhythm', () => {
  const w = parseRepeat('Mon 09:00, Thu 18:30');
  // Thu 1 Oct 2026, 10:00
  assert.equal(iso(firstDue(w, d('2026-10-01T00:00'), d('2026-10-01T10:00'))), '2026-10-01T18:30');
  assert.equal(iso(afterDone(w, d('2026-10-01T18:30'), d('2026-10-01T19:00'))), '2026-10-05T09:00');
  const f = parseRepeat('Every 2 weeks: Mon 09:00, Thu 18:30');
  assert.equal(iso(afterDone(f, d('2026-10-05T09:00'), d('2026-10-05T10:00'))), '2026-10-08T18:30');
  assert.equal(iso(afterDone(f, d('2026-10-08T18:30'), d('2026-10-08T19:00'))), '2026-10-19T09:00');
  // started mid-week: nothing before the start
  assert.equal(iso(firstDue(f, d('2026-10-02T00:00'), d('2026-10-01T10:00'))), '2026-10-12T09:00');
});

test('monthly, including short months and the last day', () => {
  const m = parseRepeat('Monthly on the 31st 10:00');
  assert.equal(iso(afterDone(m, d('2026-10-31T10:00'), d('2026-10-31T11:00'))), '2026-11-30T10:00');
  const q = parseRepeat('Every 3 months on the last day 10:00');
  assert.equal(iso(firstDue(q, d('2026-10-01T00:00'), d('2026-10-01T09:00'))), '2026-10-31T10:00');
  assert.equal(iso(afterDone(q, d('2026-10-31T10:00'), d('2026-11-02T09:00'))), '2027-01-31T10:00');
  const feb = parseRepeat('Monthly on the last day 10:00');
  assert.equal(iso(afterDone(feb, d('2027-01-31T10:00'), d('2027-01-31T12:00'))), '2027-02-28T10:00');
});

test('yearly', () => {
  const y = parseRepeat('Yearly on 12 Mar 09:00');
  assert.equal(iso(firstDue(y, d('2026-10-01T00:00'), d('2026-10-01T09:00'))), '2027-03-12T09:00');
  assert.equal(iso(afterDone(y, d('2027-03-12T09:00'), d('2027-03-13T09:00'))), '2028-03-12T09:00');
});

test('after it is done counts from the tick', () => {
  const a = parseRepeat('Every 30 days after done 09:00');
  assert.equal(iso(afterDone(a, d('2026-10-01T09:00'), d('2026-10-05T20:00'))), '2026-11-04T09:00');
  const m = parseRepeat('Every month after done 09:00');
  assert.equal(iso(afterDone(m, null, d('2027-01-31T20:00'))), '2027-02-28T09:00');
  assert.equal(iso(firstDue(a, d('2026-10-01T00:00'), d('2026-10-01T08:00'))), '2026-10-01T09:00');
});

test('clock change keeps the wall time', () => {
  const w = parseRepeat('Sun 09:00');
  // clocks go back on Sun 25 Oct 2026
  assert.equal(iso(afterDone(w, d('2026-10-18T09:00'), d('2026-10-18T10:00'))), '2026-10-25T09:00');
});

test('once never repeats', () => {
  assert.equal(afterDone(parseRepeat('Once'), d('2026-10-01T09:00'), d('2026-10-02T09:00')), null);
});
