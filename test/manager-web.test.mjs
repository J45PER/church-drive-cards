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

import { canTick, kindGoesTo, kindGroups, linksOf, modeLabel, triggerChoices, validIconName, zoneRows } from '../web/manager/pages/manager-logic.js';

test('notification kinds group in order, and who gets one is read from everyone or the ticked people', () => {
  const kinds = [{ group: 'Alarm', key: 'a' }, { group: 'Cars', key: 'c' }, { group: 'Alarm', key: 'b', admin_only: true, people: ['person.sam'] }];
  assert.deepEqual(kindGroups(kinds).map(([g, l]) => [g, l.length]), [['Alarm', 2], ['Cars', 1]]);
  assert.equal(kindGoesTo(kinds[2], 'person.sam'), true); assert.equal(kindGoesTo(kinds[2], 'person.kim'), false); assert.equal(kindGoesTo({ all: true }, 'x'), true);
  assert.equal(canTick(kinds[2], { admin: false }), false); assert.equal(canTick(kinds[2], { admin: true }), true);
});

test('camera links are read per mode, and only movement-like sensors not yet linked are offered', () => {
  const links = { away: { 'binary_sensor.door': { cams: ['cam1'], secs: 45 } } };
  assert.deepEqual(linksOf(links, 'away'), [{ trigger: 'binary_sensor.door', cams: ['cam1'], secs: 45 }]); assert.deepEqual(linksOf(links, 'home'), []);
  const s = (id, dc) => ({ entity_id: id, state: 'off', attributes: { device_class: dc, friendly_name: id } });
  const states = Object.fromEntries([s('binary_sensor.door', 'door'), s('binary_sensor.hall', 'motion'), s('binary_sensor.leak', 'moisture'), s('sensor.x', 'motion')].map((e) => [e.entity_id, e]));
  assert.deepEqual(triggerChoices(states, new Set(['binary_sensor.door'])).map((e) => e.entity_id), ['binary_sensor.hall']);
  assert.equal(modeLabel('away'), 'Away'); assert.equal(modeLabel('armed_night'), 'Armed night');
});

test('icon names need mdi: and lower-case words; zones list home first with who is in them', () => {
  assert.equal(validIconName('mdi:fan-speed-1'), true); assert.equal(validIconName('fan'), false); assert.equal(validIconName('mdi:Fan'), false);
  const z = (id, n, r) => ({ entity_id: id, state: '0', attributes: { friendly_name: n, radius: r } });
  const p = { entity_id: 'person.sam', state: 'Gym', attributes: { friendly_name: 'Sam' } };
  const rows = zoneRows(Object.fromEntries([z('zone.gym', 'Gym', 80.4), z('zone.home', 'Home', 100), p].map((e) => [e.entity_id, e])));
  assert.deepEqual(rows.map((r) => [r.name, r.radius, r.people]), [['Home', 100, []], ['Gym', 80, ['Sam']]]);
});

import { builtHere, managerSections } from '../web/manager/pages/manager.js';

test('the Manager dashboard gives its sections with their cards, stacks taken apart, and says which the site builds', () => {
  const config = { views: [{ sections: [{ cards: [{ type: 'custom:auto-layout-card', cards: [
    { type: 'custom:section-panel-card', title: 'Notifications', icon: 'mdi:bell', color: 'neutral', cards: [{ type: 'custom:notifications-card' }] },
    { type: 'custom:section-panel-card', title: 'To-do settings', cards: [{ type: 'vertical-stack', cards: [{ type: 'heading' }, { type: 'entities' }, { type: 'markdown' }] }] },
    { type: 'custom:section-panel-card', title: 'Watch', cards: [{ type: 'custom:watch-settings-card' }] },
    { type: 'custom:section-panel-card', title: '', cards: [] }] }] }] }] };
  const s = managerSections(config);
  assert.deepEqual(s.map((x) => x.title), ['Notifications', 'To-do settings', 'Watch']);
  assert.deepEqual(s[1].cards.map((c) => c.type), ['heading', 'entities', 'markdown']);
  assert.deepEqual(s.map(builtHere), [true, true, true]); // the Watch is built on the site too
  assert.equal(builtHere({ cards: [{ type: 'custom:watch-settings-card' }, { type: 'custom:house-tasks-card' }] }), false);
});

import { contrast, paletteWarnings, slugOk } from '../web/manager/pages/logic.js';
import { describe } from '../web/manager/pages/changes.js';

test('palette checks match the house: faint text on Dark and repeated colours are flagged', () => {
  assert.ok(Math.abs(contrast('#000000', '#FFFFFF') - 21) < 0.1);
  assert.deepEqual(paletteWarnings(['#FFC857', '#4E4128', '#D5A190', '#FFE4AB', '#6E572D']), []);
  assert.match(paletteWarnings(['#FFC857', '#EEEEEE', '#D5A190', '#FFE4AB', '#6E572D'])[0], /hard to read/);
  assert.match(paletteWarnings(['#112233', '#112233', '#112233', '#112233', '#112233']).join(' '), /same/);
  assert.match(paletteWarnings(['#12', '#112233', '#112233', '#112233', '#112233'])[0], /#rrggbb/);
  assert.equal(slugOk('my_palette'), true); assert.equal(slugOk('My Palette'), false);
});

test('a change is described in a line', () => {
  assert.equal(describe({ mode: 'set', palettes: { lights: {}, gregg: {} } }), 'Change lights, gregg');
  assert.match(describe({ mode: 'replace', palettes: {} }), /past version/);
  assert.match(describe({ mode: 'reset', keys: ['a'] }), /Put back a/);
});

import { compareVersions, parseVersion, summary as versionSummary, testBuilds, versionRows } from '../web/manager/pages/versions.js';

test('versions compare as numbers, and the rows say who is behind or ahead of the website', () => {
  assert.deepEqual(parseVersion('v0.133.0'), [0, 133, 0]); assert.equal(parseVersion('nothing'), null);
  assert.equal(compareVersions('0.99.0', '0.133.0'), -1); assert.equal(compareVersions('0.133.0', '0.133.0'), 0); assert.equal(compareVersions('1.0.0', '0.133.9'), 1);
  const builds = testBuilds([{ tag_name: 'admin-latest', name: 'Clive Admin 0.132.0 (test build 880)' }, { tag_name: 'todo-latest', name: 'Church Drive To-Do 0.133.0 (test build 12)' }]);
  assert.deepEqual(builds[3], { name: 'Clive Admin app', version: '0.132.0', build: '880' }); assert.equal(builds[0].version, null);
  const rows = versionRows({ site: '0.133.0', integration: '0.133.0', release: '0.133.0', builds });
  assert.deepEqual(rows.map((r) => r.status), ['same', 'same', 'same', 'unknown', 'same', 'unknown', 'behind']);
  assert.match(versionSummary(rows).text, /Clive Admin app is behind \(0\.132\.0\)/);
  const same = versionRows({ site: '0.133.0', integration: '0.133.0', release: '0.133.0', builds: builds.map((b) => ({ ...b, version: '0.133.0' })) });
  assert.deepEqual(versionSummary(same), { tone: 'ok', text: 'Everything is on 0.133.0.' });
});

import { CARDS, CARD_GROUPS } from '../web/manager/pages/cards-data.js';
import { panelsOf } from '../web/manager/pages/design.js';

test('every card in the list belongs to a known group and has a suggested palette', () => {
  assert.ok(CARDS.length >= 60);
  for (const c of CARDS) { assert.ok(CARD_GROUPS.includes(c.group), `${c.id}: ${c.group}`); assert.match(c.id, /^[a-z0-9][a-z0-9-]{0,39}$/); assert.match(c.suggested, /^[a-z][a-z0-9_]*$/); }
  assert.equal(new Set(CARDS.map((c) => c.id)).size, CARDS.length);
});

test('a view gives its titled panels in order, and card, icon and layout changes are described', () => {
  const view = { sections: [{ cards: [{ type: 'custom:auto-layout-card', cards: [{ type: 'custom:section-panel-card', title: 'Octopus Gas', icon: 'phu:octopusenergy', cards: [] }, { type: 'custom:section-panel-card', title: 'Octoplus', icon: 'cd:gift' }, { type: 'custom:section-panel-card', title: '' }] }] }] };
  assert.deepEqual(panelsOf(view), [{ title: 'Octopus Gas', icon: 'phu:octopusenergy' }, { title: 'Octoplus', icon: 'cd:gift' }]);
  assert.match(describe({ cards: { fan: {}, gas: {} } }, 'cards'), /^Card picks: fan, gas$/);
  assert.match(describe({ dashboard: 'dashboard-mobile', view: 'energy', panel: 'Octopus Gas', icon: 'cd:bolt', before: 'phu:octopusenergy' }, 'icons'), /Octopus Gas .*phu:octopusenergy → cd:bolt/);
  assert.match(describe({ dashboard: 'd', view: 'v', order: ['A'], hidden: ['B'] }, 'layouts'), /hiding B/);
});

import { readFileSync } from 'node:fs';

test("the site's copy of the watch rules is the dashboard card's own file", () => {
  assert.equal(readFileSync(new URL('../web/manager/pages/watch-logic.js', import.meta.url), 'utf8'), readFileSync(new URL('../src/watch-logic.js', import.meta.url), 'utf8'));
});

import { mediaLine, mediaPlayers, supports, systemRows } from '../web/manager/pages/devices.js';

test('media players list what is playing first, say what is on, and systems say what is wrong', () => {
  const m = (id, state, a = {}) => ({ entity_id: id, state, attributes: { friendly_name: id.split('.')[1], ...a } });
  const st = Object.fromEntries([m('media_player.b_tv', 'off'), m('media_player.a_speaker', 'playing', { media_title: 'Song', app_name: 'Spotify', supported_features: 4 | 16 }), m('media_player.c', 'unavailable')].map((e) => [e.entity_id, e]));
  assert.deepEqual(mediaPlayers(st).map((p) => p.entity_id), ['media_player.a_speaker', 'media_player.b_tv', 'media_player.c']);
  assert.equal(mediaLine(st['media_player.a_speaker']), 'Song · Spotify'); assert.equal(mediaLine(st['media_player.b_tv']), 'Off'); assert.equal(mediaLine(st['media_player.c']), 'Unavailable');
  assert.equal(supports(st['media_player.a_speaker'], 4), true); assert.equal(supports(st['media_player.b_tv'], 4), false);
  const now = Date.parse('2026-11-11T12:00:00Z');
  const sys = Object.fromEntries([{ entity_id: 'binary_sensor.eero_wan_status', state: 'off', attributes: { device_class: 'connectivity' } }, { entity_id: 'sensor.backup_last_successful_automatic_backup', state: '2026-11-01T01:00:00Z', attributes: {} },
    { entity_id: 'update.core', state: 'on', attributes: { title: 'Home Assistant Core' } }].map((e) => [e.entity_id, e]));
  const rows = systemRows(sys, now);
  assert.deepEqual(rows.map((r) => [r.name, r.bad]), [['Internet', true], ['Backups', true], ['Updates', false]]);
  assert.equal(rows[2].sub, 'Home Assistant Core'); assert.equal(rows[2].pill, '1');
});

import { buildWeek, chargerStatus, findCars, planLine, planSlots, weekIds, weekStatIds } from '../web/manager/pages/energy-more.js';
test('the week: statistic ids come from the rate sensors, days up to yesterday are summed and split', () => {
  const states = { 'sensor.octopus_energy_electricity_24l_1_current_rate': {}, 'sensor.octopus_energy_gas_e6s_7_current_rate': {} };
  const ids = weekIds(states);
  assert.equal(ids.elecCost, 'octopus_energy:electricity_24l_1_previous_accumulative_cost');
  assert.equal(ids.gasKwh, 'octopus_energy:gas_e6s_7_previous_accumulative_consumption_kwh');
  assert.equal(weekStatIds(weekIds({})).length, 0);
  const now = Date.parse('2026-11-11T12:00:00Z'), day = (n) => now - n * 86400000;
  const r = { [ids.elecCost]: [{ start: day(2), change: 3 }, { start: day(1), change: 1 }], [ids.gasCost]: [{ start: day(1), change: 1 }] };
  const w = buildWeek(r, ids, now);
  assert.equal(w.days.length, 7);
  assert.equal(w.cost, 5);
  assert.equal(w.average, 2.5);
  assert.deepEqual(w.balance, { elec: 80, gas: 20 });
  assert.equal(w.latest.total, 2);
  assert.equal(buildWeek({}, ids, now).latest, null);
});
test('the charger, the plan and the cars', () => {
  const st = (id, state, attributes = {}) => [id, { entity_id: id, state, attributes }];
  assert.equal(chargerStatus(Object.fromEntries([st('select.zappi_charge_mode', 'Eco'), st('sensor.zappi_charging_power', '7200')])), 'Charging · 7.2 kW');
  assert.equal(chargerStatus(Object.fromEntries([st('select.zappi_charge_mode', 'Eco'), st('sensor.zappi_plug_status', 'Waiting for car')])), 'Waiting for car');
  const now = Date.parse('2026-11-11T01:00:00Z');
  const plan = planSlots(Object.fromEntries([st('binary_sensor.octopus_energy_x_intelligent_dispatching', 'on', { planned_dispatches: [{ start: '2026-11-11T02:30:00Z', end: '2026-11-11T04:30:00Z', charge_in_kwh: -15.6, source: 'smart' }, { start: '2026-11-10T01:00:00Z', end: '2026-11-10T02:00:00Z' }] })]), now);
  assert.equal(plan.length, 1);
  assert.match(planLine(plan[0], now), /to .* · 15\.6 kWh · Smart$/);
  assert.equal(planSlots({}, now), null);
  const cars = findCars(Object.fromEntries([st('sensor.id5_battery', '80', { device_class: 'battery', unit_of_measurement: '%', friendly_name: 'ID5 battery' }), st('sensor.id5_range', '210', { unit_of_measurement: 'mi' }),
    st('sensor.phone_battery', '50', { device_class: 'battery', unit_of_measurement: '%' }), st('sensor.id5_12v_battery', '90', { device_class: 'battery', unit_of_measurement: '%' })]));
  assert.deepEqual(cars.map((c) => [c.name, c.battery, c.range]), [['ID5', 80, 210]]);
});
