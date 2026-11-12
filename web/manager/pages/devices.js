// The Devices page (ported from the apps' MediaCard.kt and SystemRows in EnergyData.kt): the house's TVs and speakers, and how its systems are.
import { BRANDS } from './brands.js';
import { NA, circ, esc, hhmm, ic, pill, states as readStates, tagp, wcard } from '../ui.js';
import { fillIcons } from '../icons.js';
import { cleanup } from '../kit.js';

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


// ---- The page, as signed off on the example site: three header pills, TVs & speakers (each row opens in place, with the app's own logo and controls) and
// Devices & services (a pill only when something is wrong or waiting; Restart needed and the update count open the right part of Home Assistant) ----

/** What each app gets for controls: a film or series app has back, forward and next; YouTube has previous and next; music has shuffle and repeat; live TV has channels. */
const STREAM = { ctl: ['back10', 'play', 'fwd10', 'nextep'] }, VIDEO = { ctl: ['prev', 'play', 'next'] }, MUSIC = { ctl: ['prev', 'play', 'next'], extra: ['shuffle', 'repeat'] };
const APPS = [['youtube music', { logo: 'youtubemusic', ...MUSIC }], ['youtube', { logo: 'youtube', ...VIDEO }], ['netflix', { logo: 'netflix', ...STREAM }], ['disney', { logo: 'disneyplus', ...STREAM }], ['prime', { logo: 'primevideo', ...STREAM }],
  ['iplayer', { logo: 'bbciplayer', ...STREAM }], ['channel 4', { logo: 'channel4', ...STREAM }], ['itvx', STREAM], ['now', { logo: 'now', ...STREAM }], ['apple tv', { logo: 'appletv', ...STREAM }], ['plex', { logo: 'plex', ...STREAM }],
  ['hbo', { logo: 'hbomax', ...STREAM }], ['tubi', { logo: 'tubi', ...STREAM }], ['spotify', { logo: 'spotify', ...MUSIC }], ['apple music', { logo: 'applemusic', ...MUSIC }], ['sounds', { logo: 'bbc', ...MUSIC }], ['radio', MUSIC],
  ['live tv', { ctl: ['chdn', 'chup'] }]];
const CONTROL = {
  back10: { t: '−10s', l: 'Back 10 seconds', svc: 'media_seek', rel: -10 }, fwd10: { t: '+10s', l: 'Forward 10 seconds', svc: 'media_seek', rel: 10 }, nextep: { i: 'skip-next', t: 'Next', l: 'Next episode', svc: 'media_next_track' },
  prev: { i: 'skip-previous', l: 'Previous', svc: 'media_previous_track' }, next: { i: 'skip-next', l: 'Next', svc: 'media_next_track' }, chup: { i: 'arrow-up', t: 'Ch', l: 'Channel up', svc: 'media_next_track' }, chdn: { i: 'arrow-down', t: 'Ch', l: 'Channel down', svc: 'media_previous_track' },
  shuffle: { t: 'Shuffle', l: 'Shuffle', svc: 'shuffle_set' }, repeat: { t: 'Repeat', l: 'Repeat', svc: 'repeat_set' },
};
export const appProfile = (app) => { const a = String(app || '').toLowerCase(); const m = APPS.find(([k]) => a.includes(k)); return m ? m[1] : { ctl: ['prev', 'play', 'next'] }; };
export function brandSvg(key) {
  const b = BRANDS[key]; if (!b) return '';
  const [x, y, w, hh] = b.box, pd = Math.max(w, hh) * 0.03, vw = w + 2 * pd, vh = hh + 2 * pd, ww = Math.min(40, Math.max(14, (16 * vw) / vh));
  return `<svg viewBox="${x - pd} ${y - pd} ${vw} ${vh}" width="${ww.toFixed(1)}" height="16" fill="currentColor" fill-rule="evenodd" aria-hidden="true"><path d="${b.d}"/></svg>`;
}
const m2 = (s) => { s = Math.max(0, Math.round(s)); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; };
const clockOf = (ms) => { const d = new Date(ms); if (Number.isNaN(+d)) return ''; return d.toDateString() === new Date().toDateString() ? hhmm(ms) : `${d.toLocaleDateString('en-GB', { weekday: 'short' })} ${hhmm(ms)}`; };
let open = null;

export function devicesHtml(ctx, st, extra = {}) {
  const players = mediaPlayers(st), live = (p) => p.state === 'playing' || p.state === 'paused', on = players.filter(live);
  const ups = Object.values(st).filter((e) => e.entity_id.startsWith('update.') && e.state === 'on').map((e) => String(e.attributes?.title || name(e)).replace(/ Update$/, ''));
  const internet = Object.values(st).find((e) => e.entity_id.startsWith('binary_sensor.') && e.attributes?.device_class === 'connectivity' && /wan|internet/.test(e.entity_id));
  const remote = st['binary_sensor.remote_ui'], backupLast = Object.values(st).find((e) => e.entity_id.startsWith('sensor.backup_last_successful')), backupNext = Object.values(st).find((e) => e.entity_id.startsWith('sensor.backup_next_scheduled'));
  const lastMs = Date.parse(backupLast?.state), age = Number.isFinite(lastMs) ? (Date.now() - lastMs) / 36e5 : null;
  const net = internet ? internet.state === 'on' : null;
  const pills = pill(ctx, 'play', on.length ? `<b>${on.length}</b> playing` : 'Nothing playing', on.length ? circ(ctx, 'ok') : null)
    + pill(ctx, net === false ? 'network-off' : 'globe', net === false ? '<b>Internet down</b>' : net === null ? 'Internet status not available' : 'Internet connected', net === false ? circ(ctx, 'bad') : net === null ? circ(ctx, 'warn') : circ(ctx, 'ok'))
    + pill(ctx, 'save', Number.isFinite(lastMs) ? `Backed up <b>${clockOf(lastMs)}</b>` : 'No backup yet', age == null || age > 48 ? circ(ctx, 'warn') : circ(ctx, 'ok'));
  const act = (p) => (p.state === 'playing' ? 'pause' : p.state === 'paused' ? 'play' : 'power');
  const control = (c, sel) => `<button type="button" class="mxb${sel ? ' sel' : ''}" data-c="${c}" aria-label="${CONTROL[c]?.l || c}" title="${CONTROL[c]?.l || c}">${CONTROL[c]?.i ? ic(CONTROL[c].i, 22) : ''}${CONTROL[c]?.t ? `<span>${CONTROL[c].t}</span>` : ''}</button>`;
  const rows = players.length ? players.map((p) => {
    const isLive = live(p), isOpen = open === p.entity_id && p.state !== 'unavailable', a = p.attributes || {}, app = a.app_name || a.source || '', pr = isLive ? appProfile(app) : null;
    const logo = pr?.logo && BRANDS[pr.logo] ? `<span class="alogo">${brandSvg(pr.logo)}</span>` : '';
    const title = a.media_title || '', sub = a.media_series_title || a.media_artist || a.media_channel || '';
    const line = isLive ? [app, title].filter(Boolean).join(' · ') || (p.state === 'paused' ? 'Paused' : 'Playing') : p.state === 'unavailable' ? 'Unavailable' : p.state === 'off' ? 'Off' : label(p.state);
    let body = '';
    if (isOpen) {
      const pos = Number(a.media_position), dur = Number(a.media_duration), vol = a.volume_level != null ? Math.round(a.volume_level * 100) : null;
      const cs = !isLive ? control('power') : [control('power', true)].concat(pr.ctl.map((c) => (c === 'play' ? `<button type="button" class="mxb sel" data-c="play" aria-label="${p.state === 'playing' ? 'Pause' : 'Play'}">${ic(p.state === 'playing' ? 'pause' : 'play', 22)}</button>` : control(c)))).join('');
      const ex = isLive && pr.extra ? pr.extra.map((c) => control(c)).join('') : '';
      body = `<div class="mx"><div class="mxn">${isLive ? `<span class="mxapp">${logo}<b>${esc(app || 'Playing')}</b></span>` : ''}<b class="mxt">${isLive ? (title ? esc(title) : '<span class="na">Title not available</span>') : 'Off'}</b>${isLive && sub ? `<small>${esc(sub)}</small>` : ''}</div>
        ${isLive && dur > 0 ? `<div class="mxp"><span>${m2(pos || 0)}</span><div class="mxpb"><i style="width:${Math.min(100, ((pos || 0) / dur) * 100)}%"></i></div><span>${m2(dur)}</span></div>` : ''}
        <div class="mxc">${cs}</div>${ex ? `<div class="mxc">${ex}</div>` : ''}
        ${isLive && vol != null && supports(p, FEATURE.VOLUME_SET) ? `<label class="mxv">${ic(a.is_volume_muted ? 'volume-mute' : 'volume-high', 22)}<input type="range" min="0" max="100" value="${vol}" data-vol aria-label="Volume"><b>${vol}%</b></label>` : ''}</div>`;
    }
    return `<div class="mrw${isOpen ? ' open' : ''}" data-id="${esc(p.entity_id)}"><div class="mr${p.state === 'unavailable' ? ' off' : ''}" ${p.state === 'unavailable' ? '' : `data-mx="${esc(p.entity_id)}" role="button" tabindex="0" aria-expanded="${isOpen}"`}><span class="mi">${ic(/speaker/i.test(name(p)) ? 'speaker' : 'television-play', 22)}</span><span class="mt"><b>${esc(name(p))}</b><small>${logo}${esc(line)}</small></span><button type="button" class="mact${isLive ? ' on' : ''}" data-act="${act(p)}" ${p.state === 'unavailable' ? 'disabled' : ''} aria-label="${act(p) === 'power' ? 'Turn on' : act(p) === 'pause' ? 'Pause' : 'Play'}">${ic(act(p), 22)}</button>${p.state === 'unavailable' ? '' : `<span class="mchev${isOpen ? ' up' : ''}">${ic('chevron', 18)}</span>`}</div>${body}</div>`;
  }).join('') : `<p class="sub">${NA}</p>`;
  const cap = (s) => String(s).replace(/-/g, ' ').replace(/^./, (c) => c.toUpperCase());
  const link = (kind) => `data-ha="${kind}"`;
  const church = extra.church, offered = church && church.offered && church.offered !== church.version;
  const sys = [];
  if (remote) sys.push(['cloud', 'Remote access', 'Home Assistant Cloud', remote.state === 'on' ? null : 'Down', 'b']);
  if (backupNext) sys.push(['save', 'Backups', `Next ${clockOf(Date.parse(backupNext.state))}`, null, '']);
  sys.push(['update', 'Updates', ups.length ? `${ups.slice(0, 3).join(', ')}${ups.length > 3 ? ` and ${ups.length - 3} more` : ''}` : 'Up to date', ups.length ? String(ups.length) : null, 'w', 'updates']);
  if (church) sys.push(['link', 'Church Drive in Home Assistant', `${church.version} installed${offered ? `, ${church.offered} offered` : ''}`, church.restart ? 'Restart needed' : offered ? 'Update offered' : null, 'w', church.restart ? 'restart' : 'hacs']);
  const al = st['sensor.church_drive_alarm_info'], base = st['sensor.church_drive_base_station_info'], ext = st['sensor.church_drive_range_extender_info'];
  if (al) sys.push(['shield-check', 'Alarm', cap(al.state), null, '']);
  if (base) sys.push(['wifi-4', 'Base station', base.state === 'ok' ? 'Online' : cap(base.state), base.state === 'ok' ? null : 'Check', 'b']);
  if (ext) sys.push(['wifi-4', 'Range extender', ext.state === 'ok' ? 'Online' : cap(ext.state), ext.state === 'ok' ? null : 'Check', 'b']);
  const sysCard = sys.map((r) => `<div class="mr"><span class="mi">${ic(r[0], 22)}</span><span class="mt"><b>${r[1]}</b><small>${esc(r[2])}</small></span>${r[3] ? (r[5] ? `<button type="button" class="tagp lnk ${r[4]}" ${link(r[5])} title="Open in Home Assistant">${esc(r[3])}${ic('open-in-new', 12)}</button>` : tagp(esc(r[3]), r[4])) : ''}</div>`).join('');
  const tone = net === false ? 'bad' : remote?.state === 'off' || ups.length ? 'warn' : 'ok';
  return `<div class="stack"><div class="hls">${pills}</div><div class="egrid two"><div class="ecol">${wcard(ctx, 'media', 'television-play', 'TVs &amp; speakers', '', rows)}</div><div class="ecol">${wcard(ctx, tone, 'device-server', 'Devices &amp; services', '', sysCard)}</div></div></div>`;
}

export async function renderDevices(el, ctx) {
  cleanup(ctx);
  el.innerHTML = '<p class="note">Reading the devices from the house…</p>';
  let st, church = null;
  try { st = await readStates(ctx); } catch (e) { el.innerHTML = `<p class="err" role="alert">Could not read the devices: ${esc(e.message)}</p>`; return; }
  try {
    const m = await ctx.ha.call('manifest/get', { integration: 'church_drive' }), u = st['update.church_drive_cards_update'];
    church = { version: m.version, offered: u?.attributes?.latest_version ? String(u.attributes.latest_version).replace(/^v/, '') : m.version, restart: false };
  } catch { /* the row is left out */ }
  const draw = () => { el.innerHTML = devicesHtml(ctx, st, { church }); fillIcons(el); bind(); };
  const refresh = async () => { try { st = await readStates(ctx); draw(); } catch { /* the page stays as it is */ } };
  const call = (domain, service, data, id) => ctx.ha.call('call_service', { domain, service, service_data: data, target: { entity_id: id } }).then(() => setTimeout(refresh, 600)).catch(() => {});
  function bind() {
    el.querySelectorAll('[data-mx]').forEach((r) => {
      const toggle = () => { open = open === r.dataset.mx ? null : r.dataset.mx; draw(); };
      r.addEventListener('click', (e) => { if (!e.target.closest('.mact')) toggle(); });
      r.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle(); } });
    });
    el.querySelectorAll('.mrw').forEach((row) => {
      const id = row.dataset.id, p = st[id]; if (!p) return;
      row.querySelector('.mact')?.addEventListener('click', (e) => { e.stopPropagation(); const a = e.currentTarget.dataset.act; call('media_player', a === 'power' ? 'turn_on' : 'media_play_pause', {}, id); });
      row.querySelectorAll('[data-c]').forEach((b) => b.addEventListener('click', () => {
        const c = b.dataset.c, d = CONTROL[c];
        if (c === 'power') return call('media_player', OFF.has(p.state) ? 'turn_on' : 'turn_off', {}, id);
        if (c === 'play') return call('media_player', 'media_play_pause', {}, id);
        if (d?.svc === 'media_seek') return call('media_player', 'media_seek', { seek_position: Math.max(0, (Number(p.attributes?.media_position) || 0) + d.rel) }, id);
        if (d?.svc) return call('media_player', d.svc, c === 'shuffle' ? { shuffle: !p.attributes?.shuffle } : c === 'repeat' ? { repeat: p.attributes?.repeat === 'off' ? 'all' : 'off' } : {}, id);
      }));
      row.querySelector('[data-vol]')?.addEventListener('change', (e) => call('media_player', 'volume_set', { volume_level: Number(e.target.value) / 100 }, id));
    });
    el.querySelectorAll('[data-ha]').forEach((b) => b.addEventListener('click', () => {
      const base = String(ctx.house || '').replace(/\/$/, '');
      const to = { updates: '/config/updates', restart: '/config/system', hacs: '/hacs' }[b.dataset.ha];
      window.open(`${base}${to}`, '_blank', 'noopener');
    }));
  }
  draw();
}
