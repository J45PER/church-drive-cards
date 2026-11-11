import test from 'node:test';
import assert from 'node:assert/strict';
import { authorizeUrl, cleanAddress, parseTokens, siteId, socketUrl, tokenForm } from '../web/manager/ha.js';
import { glowColour, glowStyle, hexToRgb, inkOn } from '../web/manager/glow.js';

test('the site is its own origin with a trailing slash, which is the client id and the way back', () => {
  assert.equal(siteId({ origin: 'https://church-drive-smart-home.web.app' }), 'https://church-drive-smart-home.web.app/');
  const url = new URL(authorizeUrl('https://ha.example', 'https://site.example/', 'abc'));
  assert.equal(url.origin + url.pathname, 'https://ha.example/auth/authorize');
  assert.equal(url.searchParams.get('client_id'), 'https://site.example/');
  assert.equal(url.searchParams.get('redirect_uri'), 'https://site.example/');
  assert.equal(url.searchParams.get('state'), 'abc');
});

test('an address is tidied: https added, no trailing slash', () => {
  assert.equal(cleanAddress('  ha.example.com/ '), 'https://ha.example.com');
  assert.equal(cleanAddress('http://192.168.0.136:8123//'), 'http://192.168.0.136:8123');
  assert.equal(cleanAddress(''), '');
});

test('the token form is for a code the first time and a refresh token after', () => {
  assert.equal(tokenForm('https://s/', { code: 'c' }).get('grant_type'), 'authorization_code');
  assert.equal(tokenForm('https://s/', { code: 'c' }).get('code'), 'c');
  const r = tokenForm('https://s/', { refresh: 'r' });
  assert.equal(r.get('grant_type'), 'refresh_token');
  assert.equal(r.get('refresh_token'), 'r');
  assert.equal(r.get('client_id'), 'https://s/');
});

test('the socket address follows the house address', () => {
  assert.equal(socketUrl('https://ha.example'), 'wss://ha.example/api/websocket');
  assert.equal(socketUrl('http://192.168.0.1:8123'), 'ws://192.168.0.1:8123/api/websocket');
});

test('tokens: none without an access token, and a lifetime of at least a minute', () => {
  assert.equal(parseTokens({ error: 'invalid_grant' }), null);
  assert.equal(parseTokens(null), null);
  const t = parseTokens({ access_token: 'a', refresh_token: 'r', expires_in: 1800 }, 1_000);
  assert.deepEqual(t, { access: 'a', refresh: 'r', expires: 1_000 + 1_800_000 });
  assert.equal(parseTokens({ access_token: 'a', expires_in: 1 }, 0).expires, 60_000);
});

test('the glow is the ground\'s own hue, bright, and a second one turned 28 degrees; a grey ground has none', () => {
  assert.equal(hexToRgb('#493A1D').join(), '73,58,29');
  const a = glowColour('#493A1D', 0), b = glowColour('#493A1D', 28);
  assert.match(a, /^#[0-9a-f]{6}$/);
  assert.notEqual(a, b);
  assert.equal(glowColour('#808080', 0), null);
  assert.equal(glowColour('not a colour', 0), null);
  assert.match(glowStyle('#493A1D'), /--ground:#493A1D;--ga:#[0-9a-f]{6};--gb:#[0-9a-f]{6};/);
  assert.equal(glowStyle('#808080'), '--ground:#808080;');
});

test('text is white on a dark ground and dark on a pale one', () => {
  assert.equal(inkOn('#1D2D49'), '#ffffff');
  assert.equal(inkOn('#F7E3BA'), '#14181f');
});

import { activityNote, activityQuery, buildRegistry, cheapUntil, deviceHealthList, energySnapshot, parseActivity, percentLevel } from '../web/manager/pages/logic.js';

const st = (entity_id, state, attributes = {}, last_changed = '2026-11-11T10:00:00Z') => ({ entity_id, state, attributes, last_changed });
const reg = buildRegistry(
  { entities: [{ ei: 'sensor.door_battery', di: 'd1' }, { ei: 'sensor.door_state', di: 'd1' }, { ei: 'sensor.lamp_x', di: 'd2' }, { ei: 'sensor.phone_battery', di: 'd3', pl: 'mobile_app' }, { ei: 'sensor.hv_filter', di: 'd4', ai: 'a1' }] },
  [{ id: 'd1', name: 'Front door' }, { id: 'd2', name: 'Lamp' }, { id: 'd3', name: 'Phone' }, { id: 'd4', name: 'Air filter' }], [{ area_id: 'a1', name: 'Hall' }]);

test('device health lists batteries and filters, worst first, leaves out phones and fine plain devices', () => {
  const states = Object.fromEntries([st('sensor.door_battery', '15', { device_class: 'battery' }), st('sensor.door_state', 'on'), st('sensor.lamp_x', 'on'),
    st('sensor.phone_battery', '5', { device_class: 'battery' }), st('sensor.hv_filter', '25', { unit_of_measurement: '%' })].map((e) => [e.entity_id, e]));
  const list = deviceHealthList(states, reg);
  assert.deepEqual(list.map((d) => d.name), ['Front door', 'Air filter']);
  assert.equal(list[0].summary, 'Battery 15%'); assert.equal(list[0].level, 2);
  assert.equal(list[1].level, 1); assert.equal(list[1].area, 'Hall');
});

test('a device whose entities are all unavailable is offline, and the watch can mark one not responding', () => {
  const states = Object.fromEntries([st('sensor.lamp_x', 'unavailable'), st('sensor.door_state', 'on'), st('sensor.church_drive_device_health', 'ok', { devices: { 'sensor.door_state': { status: 'stale', reason: 'Quiet for 3 days' } } })].map((e) => [e.entity_id, e]));
  const list = deviceHealthList(states, reg);
  assert.equal(list.find((d) => d.name === 'Lamp').summary, 'Not responding');
  assert.equal(list.find((d) => d.name === 'Front door').summary, 'Quiet for 3 days');
  assert.equal(percentLevel(40, 40, 20), 1); assert.equal(percentLevel(41, 40, 20), 0);
});

test('energy finds the Octopus entities by shape and says when cheap ends', () => {
  const t = Date.parse('2026-11-11T10:00:00Z'); const half = 1800000;
  const rates = [0.1, 0.1, 0.3].map((v, i) => ({ start: new Date(t - half + i * half).toISOString(), end: new Date(t + i * half).toISOString(), value_inc_vat: v }));
  const states = Object.fromEntries([st('sensor.octopus_energy_electricity_1_2_current_rate', '0.1'), st('sensor.octopus_energy_electricity_1_2_current_accumulative_cost', '1.5'), st('event.octopus_energy_electricity_1_2_current_day_rates', 'x', { rates })].map((e) => [e.entity_id, e]));
  const e = energySnapshot(states, t + 1000);
  assert.equal(e.today, 1.5); assert.equal(e.rate, 10); assert.equal(e.rates.length, 3); assert.equal(e.cheapUntil, t + half); assert.equal(e.cheapest, 10);
  assert.equal(cheapUntil(e.rates, t + 2 * half + 1000), null);
});

test('the activity query is whole local days, the time window, kinds and person', () => {
  const now = new Date(2026, 10, 11, 15, 0, 0);
  const q = activityQuery({ day: 'yesterday', time: 'night', action: 'assistant', who: 'Sam' }, now, 50, 100);
  assert.equal(q.since, Math.floor(new Date(2026, 10, 10) / 1000)); assert.equal(q.until, Math.floor(new Date(2026, 10, 11) / 1000));
  assert.deepEqual([q.start, q.end, q.kinds, q.who, q.limit, q.offset], ['22:00', '06:00', ['clive', 'clive_auto'], 'Sam', 50, 100]);
  assert.equal('since' in activityQuery({ day: 'all' }, now), false);
});

test('activity lines are read, and automation and overrule notes say why', () => {
  const r = parseActivity({ entries: [{ ts: 1, kind: 'clive_auto', text: 'turned on the lamp', rule: 'Dusk' }, { ts: 2, text: '' }], total: 1, people: ['Sam'], assistant: 'Clive' });
  assert.equal(r.lines.length, 1); assert.equal(activityNote(r.lines[0], 'Clive'), 'automatic: Dusk'); assert.equal(parseActivity(null), null);
});
