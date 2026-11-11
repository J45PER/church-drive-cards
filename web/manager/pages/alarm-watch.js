// Alarm lights and Watch settings on the site (ported from the dashboard cards src/alarm-lights-card.js and src/watch-settings-card.js; the rules are
// the integration's, alarm_lights.py and watch.py). Changes are made straight away, as in the apps; Home Assistant checks they are by an administrator.
import { chips, h, soft } from './dom.js';
import { ALARM_MODES, CAR_FACES, CLEANING_FACES, CLIVE_FLAGS, LIGHTS_TOP, PAGES, carCandidates, climateDevices, isHidden, movePage, normalise, parseLights, setCar, setCliveFlag, setItemShown, setLightsTop, setPageOn } from './watch-logic.js';

const box = (title, ...kids) => h('article', { class: 'card plain' }, title ? h('h3', { class: 'title' }, title) : null, ...kids);
const COLOUR = { red: '#ff1744', amber: '#ff9100', blue: '#2979ff', green: '#00e676' };
const dots = (cs) => h('span', { class: 'dots-inline' }, cs.map((c) => h('i', { style: `background:${COLOUR[c]}` })));
const name = (e) => e?.attributes?.friendly_name || e?.entity_id || '';
async function states(ctx) { return Object.fromEntries((await ctx.ha.call('get_states')).map((e) => [e.entity_id, e])); }
const oldHouse = (e) => (e.code === 'unknown_command' || e.code === 'not_ready' ? 'Church Drive is not running these settings. Update Church Drive in HACS and restart Home Assistant.' : e.message);

// ---- Alarm lights ----

const METHODS = [['hue', 'Hue bridge for Hue lights (quickest)'], ['pulses', 'Home Assistant pulses for every light']];
const RULES = [['any', 'Any one sensor'], ['half', 'At least half of them'], ['all', 'All of them']];
const TESTS = [['fire', 'Fire', ['red', 'amber']], ['security_home', 'Alarm, home', ['red', 'blue']], ['security_away', 'Alarm, away', ['red']], ['fire_ended', 'Fire over', ['green']], ['disarmed', 'Alarm off', ['blue']]];
const WHAT = [['Fire, smoke, heat or CO', 'Strobe lights alternate red and amber; at night the constant lights come on bright too', 'Strobe lights green'],
  ['House alarm, set to home', 'Strobe lights alternate red and blue, constant lights bright white', 'Strobe lights blue'], ['House alarm, set to away', 'Strobe lights flash red', 'Strobe lights blue']];

export async function alarmLights(slot, ctx) {
  const st = await states(ctx);
  const ui = { when: 'auto', seconds: 20, find: '', all: false, msg: '' };
  const draw = async () => {
    let d;
    try { d = await ctx.ha.call('church_drive/alarm_lights'); } catch (e) { slot.replaceChildren(h('p', { class: 'err', role: 'alert' }, oldHouse(e))); return; }
    const c = d.config;
    const call = async (type, params) => { try { await ctx.ha.call(`church_drive/alarm_lights${type}`, params); ui.msg = ''; } catch (e) { ui.msg = e.message; } draw(); };
    const set = (change) => call('/set', change);
    const running = ['fire', 'security', 'ending'].includes(d.state);
    const testing = d.state === 'test';
    const noLights = !c.strobe.length && !c.constant.length;
    const now = { idle: 'Waiting for an alarm', fire: 'FIRE ALARM: lights running', security: `HOUSE ALARM (${d.mode === 'away' ? 'set to away' : 'set to home'}): lights running`, ending: 'All clear: putting the lights back', test: 'Testing the lights' }[d.state] || '';
    const chosen = new Set([...c.strobe, ...c.constant]);
    const find = ui.find.trim().toLowerCase();
    const lights = Object.values(st).filter((e) => e.entity_id.startsWith('light.')).map((e) => ({ id: e.entity_id, name: name(e), group: Array.isArray(e.attributes?.entity_id) }))
      .filter((l) => chosen.has(l.id) || ((ui.all || l.group) && (!find || l.name.toLowerCase().includes(find) || l.id.includes(find))))
      .sort((a, b) => Number(chosen.has(b.id)) - Number(chosen.has(a.id)) || Number(b.group) - Number(a.group) || a.name.localeCompare(b.name));
    const role = (id, r) => { const strobe = c.strobe.filter((x) => x !== id), constant = c.constant.filter((x) => x !== id); if (r === 'strobe') strobe.push(id); if (r === 'constant') constant.push(id); return set({ strobe, constant }); };
    const seg = (l) => h('span', { class: 'chips' }, [['none', 'Off', !chosen.has(l.id)], ['strobe', 'Strobe', c.strobe.includes(l.id)], ['constant', 'Constant', c.constant.includes(l.id)]]
      .map(([r, label, on]) => h('button', { type: 'button', class: 'chip', 'aria-pressed': String(on), onclick: () => role(l.id, r) }, label)));
    const sensors = Object.values(st).filter((e) => e.entity_id.startsWith('sensor.') && e.attributes?.device_class === 'illuminance').map((e) => e.entity_id);
    for (const id of c.sensors) if (!sensors.includes(id)) sensors.push(id);
    const lux = Object.fromEntries((d.sensors || []).map((r) => [r.entity_id, r.lux]));
    const num = (key, min, max, step = 1) => h('input', { class: 'field num', type: 'number', min, max, step, value: c[key], 'aria-label': key, onchange: (e) => set({ [key]: Number(e.target.value) }) });
    const field = (label, ...ctl) => h('label', { class: 'row setting' }, h('span', { style: 'flex:1' }, label), ...ctl);
    const sel = (opts, value, change, label) => h('select', { class: 'field', 'aria-label': label, onchange: (e) => change(e.target.value) }, opts.map(([v, t]) => h('option', { value: v, selected: v === value }, t)));
    slot.replaceChildren(...[
      box('Alarm lights', h('div', { class: 'chips' }, h('button', { type: 'button', class: 'chip', 'aria-pressed': String(!!c.enabled), onclick: () => set({ enabled: !c.enabled }) }, c.enabled ? 'On' : 'Off'), h('span', { class: 'tag' }, d.day ? 'Day in the house' : 'Night in the house')),
        h('p', { class: running ? 'warn-line' : 'sub', role: 'status' }, now + (testing && d.test ? ` (${(TESTS.find((t) => t[0] === d.test.test) || [])[1] || ''}, ${d.test.when})` : '')), c.enabled ? null : soft('Switched off: the lights are left alone in an alarm.')),
      box('What happens', WHAT.map(([n, play, after], i) => h('div', { class: 'kind' }, h('p', { class: 'row-title' }, dots(TESTS[i][2]), ` ${n}`), soft(play), soft(`After: ${after}`))), soft("Fire wins if both are going. When it's all over the lights go back as they were.")),
      box('Lights', soft('Strobe lights pulse. Constant lights just come on bright. A light in neither is left alone.'),
        h('div', { class: 'chips' }, h('input', { class: 'field', type: 'search', placeholder: 'Find a light', 'aria-label': 'Find a light', value: ui.find, oninput: (e) => { ui.find = e.target.value; draw(); } }),
          h('button', { type: 'button', class: 'chip', 'aria-pressed': String(ui.all), onclick: () => { ui.all = !ui.all; draw(); } }, ui.all ? 'Rooms, zones and single lights' : 'Rooms and zones only')),
        lights.length ? lights.map((l) => h('div', { class: 'row setting' }, h('span', { style: 'flex:1' }, l.name, l.group ? h('small', {}, ' room or zone') : null), seg(l))) : soft('No lights match.'),
        field('Strobe lights flash using', sel(METHODS, c.method, (v) => set({ method: v }), 'Strobe method')), field('Each Home Assistant pulse lasts (ms)', num('pulse_ms', 100, 1000, 50))),
      box('Day or night', soft('Read once, when an alarm starts, from the sensors you tick. With no readings it counts as night, which lights more.'),
        h('div', { class: 'chips' }, sensors.length ? sensors.map((id) => h('button', { type: 'button', class: 'chip', 'aria-pressed': String(c.sensors.includes(id)), onclick: () => set({ sensors: c.sensors.includes(id) ? c.sensors.filter((x) => x !== id) : [...c.sensors, id] }) }, `${name(st[id]).replace(/ illuminance$/i, '')}${c.sensors.includes(id) && lux[id] != null ? ` · ${Math.round(lux[id])} lx` : ''}`)) : [soft('No light sensors found.')]),
        field("It's day when a sensor reads at least (lx)", num('lux', 1, 100000)), field('and this many sensors say day', sel(RULES, c.rule, (v) => set({ rule: v }), 'Sensor rule'))),
      box("When it's over", field('After a fire, strobe lights green for (seconds)', num('green_seconds', 0, 600)), field('When the alarm is turned off, blue for (seconds)', num('blue_seconds', 0, 600))),
      box('Test (lights only)', soft('Plays it on the lights above and puts them back after. No alarm, no notification. Not while a real alarm is going.'),
        h('div', { class: 'chips' }, [['auto', `Now (${d.day ? 'day' : 'night'})`], ['day', 'Day'], ['night', 'Night']].map(([w, t]) => h('button', { type: 'button', class: 'chip', 'aria-pressed': String(ui.when === w), onclick: () => { ui.when = w; draw(); } }, t))),
        field('For (seconds)', h('input', { class: 'field num', type: 'number', min: 5, max: 120, value: ui.seconds, 'aria-label': 'Test seconds', onchange: (e) => { ui.seconds = Math.max(5, Math.min(120, Number(e.target.value) || 20)); draw(); } })),
        h('div', { class: 'chips' }, TESTS.map(([k, label, cs]) => h('button', { type: 'button', class: 'chip', disabled: running || noLights, onclick: () => call('/test', { test: k, when: ui.when, seconds: ui.seconds }) }, dots(cs), ` ${label}`)),
          testing ? h('button', { type: 'button', class: 'btn', onclick: () => call('/stop', {}) }, 'Stop') : null), noLights ? soft('Pick some lights first.') : null),
      (d.missing || []).length ? h('p', { class: 'warn-line' }, `Lights that no longer exist: ${d.missing.join(', ')}`) : null,
      ui.msg ? h('p', { class: 'err', role: 'status' }, ui.msg) : null,
    ].filter(Boolean));
  };
  await draw();
}

// ---- Watch settings ----

export async function watchSettings(slot, ctx) {
  const st = await states(ctx);
  let floors = [];
  try { floors = parseLights(await ctx.ha.call('lovelace/config', { url_path: 'dashboard-mobile' }), (id) => st[id]?.attributes?.friendly_name || ''); } catch { floors = []; }
  let msg = '';
  const draw = async () => {
    let cfg;
    try { cfg = normalise(await ctx.ha.call('church_drive/watch')); } catch (e) { slot.replaceChildren(h('p', { class: 'err', role: 'alert' }, oldHouse(e))); return; }
    const save = async (next) => { try { await ctx.ha.call('church_drive/watch/set', { config: next }); msg = ''; } catch (e) { msg = e.message; } draw(); };
    const items = (page, list) => h('div', { class: 'chips' }, list.map((it) => h('button', { type: 'button', class: 'chip', 'aria-pressed': String(!isHidden(cfg, page, it.id)), title: it.sub || '', onclick: () => save(setItemShown(cfg, page, it.id, isHidden(cfg, page, it.id))) }, it.label)));
    const pageBody = (id) => {
      if (id === 'alarm') return [h('p', { class: 'label' }, 'Modes shown'), items('alarm', ALARM_MODES)];
      if (id === 'cleaning') return [h('p', { class: 'label' }, 'Faces shown'), items('cleaning', CLEANING_FACES)];
      if (id === 'climate') { const d = climateDevices(st); return d.length ? [h('p', { class: 'label' }, 'Devices shown'), items('climate', d.map((x) => ({ id: x.id, label: x.name, sub: x.kind })))] : [soft('No thermostats, air con or fans found.')]; }
      if (id === 'lights') {
        const rows = []; for (const f of floors) for (const r of f.rooms) { rows.push({ id: r.head, label: r.title, sub: f.name || 'room' }); for (const z of r.zones) rows.push({ id: z.entity, label: z.name, sub: 'zone' }); }
        return [h('p', { class: 'label' }, 'Starts with'), chips(LIGHTS_TOP.map((t) => [t.label, cfg.lights_top === t.id, () => save(setLightsTop(cfg, t.id))])), rows.length ? [h('p', { class: 'label' }, 'Shown on the watch'), items('lights', rows)] : soft('No rooms found on the Mobile dashboard yet.')];
      }
      if (id === 'car') {
        const cars = carCandidates(st);
        return [h('p', { class: 'label' }, 'Faces shown'), items('car', CAR_FACES), cars.length ? [h('p', { class: 'label' }, 'Cars whose battery can show on the ring'), chips(cars.map((c) => [c.name, cfg.cars.some((x) => x.battery === c.battery), () => save(setCar(cfg, c, !cfg.cars.some((x) => x.battery === c.battery)))])), soft('The battery only shows when exactly one of these is plugged in; otherwise the ring is coloured by mode.')] : null];
      }
      return [];
    };
    slot.replaceChildren(...[
      box('Pages on the watch, in order', soft("Switch a page off to hide it, or move it up and down. Under each, tap anything you don't want there; new devices appear by themselves.")),
      ...cfg.pages.map((p, i) => { const meta = PAGES.find((x) => x.id === p.id); return box(null, h('div', { class: 'row' }, h('div', { style: 'flex:1' }, h('p', { class: 'row-title' }, meta.label), soft(meta.what)),
        h('button', { type: 'button', class: 'btn quiet', 'aria-label': `Move ${meta.label} up`, disabled: i === 0, onclick: () => save(movePage(cfg, p.id, -1)) }, '↑'),
        h('button', { type: 'button', class: 'btn quiet', 'aria-label': `Move ${meta.label} down`, disabled: i === cfg.pages.length - 1, onclick: () => save(movePage(cfg, p.id, 1)) }, '↓'),
        h('button', { type: 'button', class: 'chip', 'aria-pressed': String(p.on), onclick: () => save(setPageOn(cfg, p.id, !p.on)) }, p.on ? 'On' : 'Off')), p.on ? pageBody(p.id) : soft('Not on the watch.')); }),
      box('Clive on the watch', chips(CLIVE_FLAGS.map((f) => [f.label, !!cfg.clive[f.id], () => save(setCliveFlag(cfg, f.id, !cfg.clive[f.id]))])), soft('The starting point for these toggles; each person can still change them on the watch.')),
      msg ? h('p', { class: 'err', role: 'alert' }, msg) : null,
    ].flat().filter(Boolean));
  };
  await draw();
}
