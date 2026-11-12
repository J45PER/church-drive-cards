// The Device health page: every device that has a battery, a filter or a connection to watch, in tiles of one size, with the icon that says which kind of
// trouble it is. Three ways to look at the same list (grid, by room, list) and a switch for just the ones that need attention.
import { BAD, OK, WARN, ago, buildRegistry, deviceHealthList, healthSummary } from './logic.js';
import { fillIcons, icon } from '../icons.js';
import { cleanup } from '../kit.js';
import { circ, esc, ic, palette, pill, readableOn, states as readStates, toneVars } from '../ui.js';

const TONE_OF = { [OK]: 'ok', [WARN]: 'warn', [BAD]: 'bad' };
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

async function houseRegistry(ha) {
  const [entityReg, deviceReg, areaReg] = await Promise.all([ha.call('config/entity_registry/list_for_display'), ha.call('config/device_registry/list'), ha.call('config/area_registry/list')]);
  return buildRegistry(entityReg, deviceReg, areaReg);
}


// ---- The page, as signed off on the example site: three header pills, a Critical / Attention / Everything filter, and the devices by room as tone cards
// (green fine, amber check soon, red needs you) with a bar for each battery or filter; the level word shows only when it is not fine ----
let filter = 'all';

export function healthHtml(ctx, items) {
  const crit = items.filter((d) => d.level === BAD), attn = items.filter((d) => d.level !== OK);
  const F = filter === 'crit' || filter === 'attn' ? filter : 'all', shown = F === 'crit' ? crit : F === 'attn' ? attn : items;
  const worst = crit.length ? 'bad' : attn.length ? 'warn' : 'ok';
  const batBad = items.filter((d) => d.battery != null && d.batteryLevel === BAD).length, batLow = items.filter((d) => d.batteryLevel === WARN).length, bat = batBad + batLow;
  const off = items.filter((d) => d.offline).length;
  const pills = pill(ctx, 'device-health', attn.length ? `<b>${attn.length} of ${items.length}</b> need attention` : `<b>All ${items.length}</b> fine`, circ(ctx, worst))
    + pill(ctx, bat ? 'battery-low' : 'battery-100', bat ? `<b>${bat}</b> ${bat === 1 ? 'battery' : 'batteries'} low` : 'Batteries fine', circ(ctx, batBad ? 'bad' : batLow ? 'warn' : 'ok'))
    + pill(ctx, off ? 'wifi-off' : 'wifi-4', off ? `<b>${off}</b> not responding` : 'All responding', circ(ctx, off ? 'bad' : 'ok'));
  const chips = [['crit', `Critical (${crit.length})`, 'bad'], ['attn', `Attention (${attn.length})`, 'warn'], ['all', 'Everything', 'neutral']]
    .map(([k, l, t]) => `<button class="chip" type="button" data-hf="${k}" aria-pressed="${F === k}" style="${toneVars(ctx, t)}"><i class="fdot"></i>${l}</button>`).join('');
  const tile = (d) => {
    const tone = TONE_OF[d.level], p = palette(ctx, tone), ac = readableOn(p[0], p[3], p[1]), mv = meterOf(d);
    const prob = d.offline ? 'Not responding' : d.staleReason || '';
    const meter = mv != null ? `<div class="hdm"><div class="hdm-l"><span>${d.filter != null && (d.battery == null || d.filter <= d.battery) ? 'Filter' : 'Battery'}</span><b>${mv}%</b></div><div class="hdm-b" style="background:color-mix(in srgb,${ac} 22%,transparent)"><i style="width:${Math.max(2, Math.min(100, mv))}%;background:${ac}"></i></div></div>` : '';
    return `<article class="tcard glow hd" style="${toneVars(ctx, tone)}"><div class="hd-h"><span class="wc-i">${ic(healthIcon(d).replace(/^cd:/, ''), 26)}</span><div class="hd-n"><b>${esc(d.name)}</b>${prob ? `<small>${esc(prob)}</small>` : ''}</div>${d.level === OK ? '' : `<span class="wc-p">${LEVEL_WORD[d.level]}</span>`}</div>${meter}</article>`;
  };
  const rooms = byRoom(shown);
  const body = rooms.length ? rooms.map(([room, list]) => `<section class="stack" style="gap:8px"><div class="ph"><h2 class="sh">${esc(room)}</h2><span class="sub">${list.length}</span></div><div class="hdg">${list.map(tile).join('')}</div></section>`).join('')
    : `<p class="sub">${F === 'all' ? '<span class="na">No devices with a battery, filter or connection to watch.</span>' : 'Nothing here.'}</p>`;
  return `<div class="stack"><div class="hls">${pills}</div><div class="chips">${chips}</div>${body}</div>`;
}

export async function renderHealth(el, ctx) {
  cleanup(ctx);
  el.innerHTML = '<p class="note">Reading the devices from the house…</p>';
  let items;
  try {
    const [list, reg] = await Promise.all([ctx.ha.call('get_states'), houseRegistry(ctx.ha)]);
    items = deviceHealthList(Object.fromEntries(list.map((e) => [e.entity_id, e])), reg);
  } catch (e) { el.innerHTML = `<p class="err" role="alert">Could not read the devices: ${esc(e.message)}</p>`; return; }
  const draw = () => { el.innerHTML = healthHtml(ctx, items); fillIcons(el); el.querySelectorAll('[data-hf]').forEach((b) => b.addEventListener('click', () => { filter = b.dataset.hf; draw(); })); };
  draw();
}
