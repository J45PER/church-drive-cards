import test from 'node:test';
import assert from 'node:assert/strict';
import { Anim, FOSE, NO_CONNECTION_AFTER_MS, bigText, chooseFace, duration, faceOf, isRestartClose, pillKind, pullFraction, wallClock } from '../web/manager/status.js';

const up = { connection: 'Connected', statesLoaded: true, refreshing: false, restarting: false };

test('a settled line says nothing', () => assert.equal(pillKind(up, 0), 'none'));

test('a blip is Connecting; six seconds down is No connection', () => {
  const down = { ...up, connection: 'Disconnected' };
  assert.equal(pillKind({ ...up, connection: 'Connecting' }, 0), 'connecting');
  assert.equal(pillKind(down, NO_CONNECTION_AFTER_MS - 1), 'connecting');
  assert.equal(pillKind(down, NO_CONNECTION_AFTER_MS), 'noconnection');
});

test('loading or refreshing is Updating', () => {
  assert.equal(pillKind({ ...up, statesLoaded: false }, 0), 'updating');
  assert.equal(pillKind({ ...up, refreshing: true }, 0), 'updating');
});

test('restarting beats No connection and lasts until the page is loaded', () => {
  assert.equal(pillKind({ ...up, connection: 'Disconnected', restarting: true }, 60000), 'restarting');
  assert.equal(pillKind({ ...up, statesLoaded: false, restarting: true }, 0), 'restarting');
  assert.equal(pillKind({ ...up, restarting: true }, 0), 'none');
  assert.equal(pillKind({ ...up, connection: 'AuthFailed', restarting: true }, 0), 'none');
});

test('Home Assistant restarting is told by its close code', () => {
  for (const code of [1001, 1012, 1013]) assert.equal(isRestartClose(code), true);
  for (const code of [1000, 1006, 4000]) assert.equal(isRestartClose(code), false);
});

test('the pull: halved, 80 px, Release from 1', () => {
  assert.equal(pullFraction(160), 1);
  assert.equal(pullFraction(80), 0.5);
  assert.equal(pullFraction(-30), 0);
  assert.equal(chooseFace(up, 0, 0, 0.6), 'pull');
  assert.equal(chooseFace(up, 0, 0, 1), 'release');
  assert.equal(chooseFace({ ...up, connection: 'Disconnected', restarting: true }, 0, 0, 1.2), 'restart');
});

test('after a pull: Updating, then Up to date, and a line that is down stays as it is', () => {
  assert.equal(chooseFace({ ...up, refreshing: true }, 0, 1, 0), 'updating');
  assert.equal(chooseFace(up, 0, 1, 0), 'updating');
  assert.equal(chooseFace(up, 0, 2, 0), 'done');
  assert.equal(chooseFace({ ...up, connection: 'Disconnected' }, 7000, 1, 0), 'noconn');
  assert.equal(faceOf('none'), 'hidden');
});

test('lengths of time and times of day are written plainly', () => {
  assert.equal(duration(7400), '7 s');
  assert.equal(duration(72000), '1 min 12 s');
  assert.equal(duration(-5), '0 s');
  assert.equal(wallClock(new Date(2026, 10, 11, 12, 4, 11).getTime()), '12:04:11');
});

const info = { now: 1_000_000, host: 'home.example', page: 'Device health', attempt: 3, retryIn: 4000, lostAt: new Date(2026, 10, 11, 12, 1, 40).getTime(), restartedAt: 0, updatedAt: 1_000_000 - 42000, version: '2025.11.1', latency: 38, haState: 'RUNNING', connected: true };

test('each face says only what the site knows', () => {
  assert.deepEqual(bigText('connecting', info), { t: 'Connecting', a: 'Reaching home.example', b: 'Attempt 3 · next try in 4 s', bar: 'indeterminate' });
  assert.equal(bigText('updating', info).a, 'Reloading Device health');
  assert.equal(bigText('updating', info).b, 'Home Assistant 2025.11.1');
  assert.match(bigText('pull', info).a, /Device health · last updated .* \(42 s ago\)/);
  assert.equal(bigText('release', info).b, 'Let go to reload Device health');
  assert.equal(bigText('done', info).b, 'Home Assistant 2025.11.1 · 38 ms');
  const off = bigText('noconn', info);
  assert.equal(off.t, 'No connection'); assert.equal(off.btn, 'Try now'); assert.equal(off.b, 'Trying again in 4 s · attempt 3'); assert.match(off.a, /last up at 12:01:40/);
});

test('restarting says whether Home Assistant is back, and starting up or loading', () => {
  const r = { ...info, restartedAt: info.now - 72000 };
  assert.equal(bigText('restart', { ...r, connected: false }).b, 'Waiting for it to come back');
  assert.equal(bigText('restart', { ...r, haState: 'NOT_RUNNING' }).b, 'Home Assistant is starting up');
  assert.equal(bigText('restart', r).b, 'Loading Device health');
  assert.match(bigText('restart', r).a, /· 1 min 12 s$/);
});

test('a missing fact is left out, not invented', () => {
  const bare = { ...info, version: '', latency: 0, lostAt: 0, updatedAt: 0, retryIn: 0 };
  assert.equal(bigText('done', bare).b, 'Home Assistant is answering');
  assert.equal(bigText('noconn', bare).a, 'Can’t reach the house');
  assert.equal(bigText('noconn', bare).b, 'Trying again now · attempt 3');
  assert.equal(bigText('connecting', bare).b, 'Attempt 3');
});

test('the easing is Material\'s, and a spring settles without overshooting', () => {
  assert.equal(FOSE(0), 0); assert.equal(FOSE(1), 1);
  assert.ok(FOSE(0.5) > 0.5 && FOSE(0.5) < 1);
  const a = new Anim(0); a.set(1, { spring: 20 }, 0);
  let last = 0; for (let t = 0; t <= 600; t += 20) { const v = a.get(t); assert.ok(v >= last && v <= 1); last = v; }
  assert.equal(a.get(1000), 1);
  const b = new Anim(0); b.set(10, { dur: 200 }, 0);
  assert.equal(b.get(0), 0); assert.equal(b.get(200), 10); assert.equal(b.get(999), 10);
});

import { AWAY_MS, IDLE_MS, LIVE_EVERY_MS, dueOnReturn, duePeriodic, editing, idle } from '../web/manager/freshness.js';

test('back to the tab after a minute away reads the page again', () => {
  assert.equal(dueOnReturn(500_000, 0), false);                       // was never hidden
  assert.equal(dueOnReturn(500_000, 500_000 - AWAY_MS + 1), false);
  assert.equal(dueOnReturn(500_000, 500_000 - AWAY_MS), true);
});

test('an open page is read again every five minutes, and only when left alone', () => {
  assert.equal(duePeriodic(1_000_000, 1_000_000 - LIVE_EVERY_MS + 1), false);
  assert.equal(duePeriodic(1_000_000, 1_000_000 - LIVE_EVERY_MS), true);
  assert.equal(duePeriodic(1_000_000, 0), false);
  assert.equal(idle(100_000, 100_000 - IDLE_MS + 1), false);
  assert.equal(idle(100_000, 100_000 - IDLE_MS), true);
});

test('a field with focus or an open dialog is never redrawn under someone', () => {
  const doc = (tag, dialog) => ({ activeElement: tag ? { tagName: tag } : null, querySelector: () => (dialog ? {} : null) });
  assert.equal(editing(doc('INPUT')), true);
  assert.equal(editing(doc('SELECT')), true);
  assert.equal(editing(doc('TEXTAREA')), true);
  assert.equal(editing(doc('BODY')), false);
  assert.equal(editing(doc(null, true)), true);
  assert.equal(editing(doc(null, false)), false);
});
