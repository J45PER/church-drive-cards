// The Devices page (ported from the apps' MediaCard.kt and SystemRows in EnergyData.kt): the house's TVs and speakers, and how its systems are.
import { chips, h, soft } from './dom.js';

const PLAYING = new Set(['playing', 'paused', 'buffering']);
const OFF = new Set(['off', 'unavailable', 'unknown']);
const FEATURE = { PAUSE: 1, VOLUME_SET: 4, PREVIOUS: 16, NEXT: 32, TURN_ON: 128, TURN_OFF: 256, PLAY: 16384 };
const label = (s) => String(s || '').replace(/[_-]+/g, ' ').replace(/^./, (c) => c.toUpperCase());
const name = (e) => e?.attributes?.friendly_name || e?.entity_id || '';
export const supports = (e, flag) => ((Number(e?.attributes?.supported_features) || 0) & flag) !== 0;

/** Every media player, whatever is playing first and then by name. */
export function mediaPlayers(states) {
  return Object.values(states).filter((e) => e.entity_id.startsWith('media_player.')).sort((a, b) => (PLAYING.has(a.state) ? 0 : 1) - (PLAYING.has(b.state) ? 0 : 1) || name(a).toLowerCase().localeCompare(name(b).toLowerCase()));
}
/** What a row says under the name: what is playing, else the state in words. */
export function mediaLine(p) {
  if (PLAYING.has(p.state)) return [p.attributes?.media_title, p.attributes?.app_name].filter((x) => x && String(x).trim()).join(' · ') || label(p.state);
  if (p.state === 'unavailable') return 'Unavailable';
  if (p.state === 'off') return 'Off';
  return label(p.state);
}

const DAY = 86400000;
/** How the house's systems are: internet, remote access, backups, updates. Each row is {name, sub, pill, bad}. */
export function systemRows(states, now = Date.now()) {
  const all = Object.values(states); const rows = [];
  const internet = all.find((e) => e.entity_id.startsWith('binary_sensor.') && e.attributes?.device_class === 'connectivity' && /wan|internet/.test(e.entity_id));
  if (internet) { const up = internet.state === 'on'; rows.push({ name: 'Internet', sub: up ? 'Connected' : 'Down', pill: up ? 'Online' : 'Down', bad: !up }); }
  const remote = states['binary_sensor.remote_ui'];
  if (remote) { const up = remote.state === 'on'; rows.push({ name: 'Remote access', sub: 'Home Assistant Cloud', pill: up ? 'On' : 'Down', bad: !up }); }
  const last = all.find((e) => e.entity_id.startsWith('sensor.backup_last_successful')), next = all.find((e) => e.entity_id.startsWith('sensor.backup_next_scheduled'));
  if (last || next) {
    const lastMs = Date.parse(last?.state), nextMs = Date.parse(next?.state); const clock = (ms) => new Date(ms).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
    const old = Number.isFinite(lastMs) && now - lastMs > 2 * DAY;
    rows.push({ name: 'Backups', sub: [Number.isFinite(lastMs) ? `Last ${clock(lastMs)}` : 'No backup yet', Number.isFinite(nextMs) ? `next ${clock(nextMs)}` : null].filter(Boolean).join(' · '), pill: old ? 'Overdue' : null, bad: old });
  }
  const updates = all.filter((e) => e.entity_id.startsWith('update.') && e.state === 'on');
  rows.push({ name: 'Updates', sub: updates.length ? `${updates.slice(0, 3).map((u) => u.attributes?.title || name(u)).join(', ')}${updates.length > 3 ? ` and ${updates.length - 3} more` : ''}` : 'Up to date', pill: updates.length ? String(updates.length) : null, bad: false });
  return rows;
}

export async function renderDevices(el, ctx) {
  el.replaceChildren(h('p', { class: 'note' }, 'Reading the devices from the house…'));
  const load = async () => Object.fromEntries((await ctx.ha.call('get_states')).map((e) => [e.entity_id, e]));
  const call = async (domain, service, entity_id, data) => { try { await ctx.ha.call('call_service', { domain, service, service_data: data, target: { entity_id } }); } catch (e) { msg.textContent = e.message; } };
  const msg = h('p', { class: 'err', role: 'status' });
  const draw = async () => {
    let st; try { st = await load(); } catch (e) { el.replaceChildren(h('p', { class: 'err', role: 'alert' }, `Could not read the devices: ${e.message}`)); return; }
    const players = mediaPlayers(st);
    const player = (p) => {
      const on = !OFF.has(p.state); const playing = PLAYING.has(p.state); const id = p.entity_id;
      const act = async (domain, service, data) => { await call(domain, service, id, data); setTimeout(draw, 600); };
      return h('div', { class: 'kind' },
        h('div', { class: 'row' }, h('div', { style: 'flex:1' }, h('p', { class: 'row-title' }, name(p)), soft(mediaLine(p))),
          p.state !== 'unavailable' ? chips([
            supports(p, FEATURE.PREVIOUS) && on ? ['⏮', false, () => act('media_player', 'media_previous_track')] : null,
            [playing ? (p.state === 'playing' ? 'Pause' : 'Play') : on ? 'Turn off' : 'Turn on', on, () => (playing ? act('media_player', 'media_play_pause') : act('media_player', on ? 'turn_off' : 'turn_on'))],
            supports(p, FEATURE.NEXT) && on ? ['⏭', false, () => act('media_player', 'media_next_track')] : null,
          ].filter(Boolean)) : null),
        on && supports(p, FEATURE.VOLUME_SET) ? h('label', { class: 'row setting' }, h('span', { style: 'flex:1' }, 'Volume'),
          h('input', { type: 'range', min: 0, max: 1, step: 0.01, value: Number(p.attributes?.volume_level) || 0, 'aria-label': `Volume of ${name(p)}`, onchange: (e) => act('media_player', 'volume_set', { volume_level: Number(e.target.value) }) })) : null);
    };
    el.replaceChildren(h('div', { class: 'stack' },
      h('section', { class: 'stack', style: 'gap:10px' }, h('div', { class: 'head' }, h('h2', {}, 'TVs & speakers'), h('span', { class: 'count' }, players.filter((p) => PLAYING.has(p.state)).length ? `${players.filter((p) => p.state === 'playing').length} playing` : 'Nothing playing')),
        h('article', { class: 'card plain' }, players.length ? players.map(player) : soft('No TVs or speakers found.'))),
      h('section', { class: 'stack', style: 'gap:10px' }, h('div', { class: 'head' }, h('h2', {}, 'Devices & services')),
        h('article', { class: 'card plain' }, systemRows(st).map((r) => h('div', { class: 'row setting' }, h('div', { style: 'flex:1' }, h('p', { class: 'row-title' }, r.name), soft(r.sub)), r.pill ? h('span', { class: `tag ${r.bad ? 'behind' : 'same'}` }, r.pill) : null)))),
      msg));
  };
  await draw();
}
