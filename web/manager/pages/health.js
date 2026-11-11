// The Device health page: every device that has a battery, a filter or a connection to watch, in tiles of one size, with the icon that says which kind of
// trouble it is. Three ways to look at the same list (grid, by room, list) and a switch for just the ones that need attention.
import { chips, h, soft } from './dom.js';
import { BAD, OK, WARN, ago, buildRegistry, deviceHealthList, healthSummary } from './logic.js';
import { fillIcons, icon } from '../icons.js';
import { panel, toneStyle } from '../kit.js';

const TONE = { [OK]: 'ok', [WARN]: 'warn', [BAD]: 'bad' };
const LEVEL_WORD = { [OK]: 'Fine', [WARN]: 'Check soon', [BAD]: 'Needs you' };

/** The icon that says what is wrong (or fine) with a device: not responding, a battery, a filter, a stale reading. */
export function healthIcon(d) {
  if (d.offline) return 'cd:wifi-off';
  if (d.staleReason) return 'cd:clock-alert';
  if (d.batteryLevel != null && d.batteryLevel !== OK) return d.battery != null && d.battery <= 20 ? 'cd:battery-0' : 'cd:battery-low';
  if (d.filterLevel != null && d.filterLevel !== OK) return 'cd:air-filter';
  if (d.battery != null) return d.battery >= 75 ? 'cd:battery-100' : d.battery >= 35 ? 'cd:battery-50' : 'cd:battery-low';
  if (d.filter != null) return 'cd:air-filter';
  return 'cd:device-health';
}
/** The meter along the bottom of a tile: the battery or filter percentage, the lower of the two. */
export const meterOf = (d) => { const v = [d.battery, d.filter].filter((x) => x != null); return v.length ? Math.min(...v) : null; };
/** Devices grouped by room (those with none under "No room"), rooms by name. */
export function byRoom(items) {
  const m = new Map(); for (const d of items) { const k = d.area || 'No room'; if (!m.has(k)) m.set(k, []); m.get(k).push(d); }
  return [...m].sort(([a], [b]) => (a === 'No room') - (b === 'No room') || a.localeCompare(b));
}

const svgOf = (code, size) => { const t = document.createElement('template'); t.innerHTML = icon(code, size); return t.content.firstChild; };
async function houseRegistry(ha) {
  const [entityReg, deviceReg, areaReg] = await Promise.all([ha.call('config/entity_registry/list_for_display'), ha.call('config/device_registry/list'), ha.call('config/area_registry/list')]);
  return buildRegistry(entityReg, deviceReg, areaReg);
}

const view = { mode: 'grid', only: false };
export async function renderHealth(el, ctx) {
  el.replaceChildren(h('p', { class: 'note' }, 'Reading the devices from the house…'));
  let items;
  try {
    const [list, reg] = await Promise.all([ctx.ha.call('get_states'), houseRegistry(ctx.ha)]);
    items = deviceHealthList(Object.fromEntries(list.map((e) => [e.entity_id, e])), reg);
  } catch (e) { el.replaceChildren(h('p', { class: 'err', role: 'alert' }, `Could not read the devices: ${e.message}`)); return; }

  const tile = (d) => h('article', { class: `hd ${view.mode === 'list' ? 'hd-row' : ''}`, style: toneStyle(ctx, TONE[d.level]) },
    h('span', { class: 'hd-ico' }, svgOf(healthIcon(d), 26)),
    h('div', { class: 'hd-main' }, h('p', { class: 'hd-name' }, d.name), h('p', { class: 'hd-sub' }, d.summary || 'Reporting in')),
    h('div', { class: 'hd-side' }, d.area && view.mode !== 'room' ? h('span', { class: 'tag' }, d.area) : null, h('span', { class: `hd-state s${d.level}` }, LEVEL_WORD[d.level]),
      d.heard ? h('span', { class: 'hd-heard' }, ago(d.heard)) : null),
    meterOf(d) != null ? h('div', { class: 'hd-meter', 'aria-label': `${meterOf(d)}%` }, h('i', { style: `width:${meterOf(d)}%` })) : null);
  const body = h('div', { class: 'stack', style: 'gap:18px' });
  const worst = items.some((d) => d.level === BAD) ? 'bad' : items.some((d) => d.level === WARN) ? 'warn' : 'ok';
  const box = panel(ctx, { title: 'Device health', icon: 'cd:device-health', color: worst, summary: healthSummary(items) });
  const draw = () => {
    const shown = view.only ? items.filter((d) => d.level !== OK) : items;
    controls.replaceChildren(chips([['Grid', view.mode === 'grid', () => { view.mode = 'grid'; draw(); }], ['By room', view.mode === 'room', () => { view.mode = 'room'; draw(); }], ['List', view.mode === 'list', () => { view.mode = 'list'; draw(); }]]),
      chips([['Everything', !view.only, () => { view.only = false; draw(); }], [`Needs attention (${items.filter((d) => d.level !== OK).length})`, view.only, () => { view.only = true; draw(); }]]));
    if (!shown.length) { body.replaceChildren(soft(view.only ? 'Nothing needs attention.' : 'No devices with a battery, filter or connection to watch.')); return; }
    body.replaceChildren(...(view.mode === 'room'
      ? byRoom(shown).map(([room, list]) => h('section', { class: 'stack', style: 'gap:10px' }, h('div', { class: 'head' }, h('h2', {}, room), h('span', { class: 'count' }, `${list.length}`)), h('div', { class: 'hd-grid' }, list.map(tile))))
      : [h('div', { class: view.mode === 'list' ? 'hd-list' : 'hd-grid' }, shown.map(tile))]));
    fillIcons(body);
  };
  const controls = h('div', { class: 'hd-controls' });
  box.querySelector('.panel-body').replaceChildren(controls, body);
  el.replaceChildren(box); draw();
}
