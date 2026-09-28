// Security Zone Card: one outside or entry zone (Front Garden, Entrance,
// Driveway…) at a glance. Title in the zone's colour with its state word
// ("Motion just now", "Closed", "Open 4 min"), a strip of the last few hours
// (hourly bars or ticks: motion, door open, doorbell, light on) that you can
// press and hold to read, the last events, each battery on its own row, and
// a tile for the zone's light. Indigo normally, amber while a door is open,
// red on a tamper or a door opened while the alarm is set.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { KIT_COLOR, kitShell, kitHead, kitTiles, kitNum, kitEsc, KitHistory, kitStateHistory, kitCompact, kitCompactable } from './card-kit.js';

export const SZ_COLOR = {
  zone: '#5c6bc0',
  motion: '#7986cb',
  door: KIT_COLOR.fair,
  ring: '#f06292',
  tamper: KIT_COLOR.bad,
  light: '#ffd54f',
};
const SZ_WORD = { motion: 'Motion', door: 'Door open', ring: 'Doorbell', tamper: 'Tamper', light: 'Light on' };
const SZ_BATTERIES = [1, 2, 3, 4];
const RECENT = 120e3; // "just now"
const LOW = 25;

const isEvent = (id) => String(id || '').startsWith('event.');
const clock = (ms) => new Date(ms).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
function when(ms) {
  const d = new Date(ms), today = new Date();
  const yesterday = new Date(today.getTime() - 864e5);
  if (d.toDateString() === today.toDateString()) return clock(ms);
  if (d.toDateString() === yesterday.toDateString()) return `yesterday ${clock(ms)}`;
  return `${d.toLocaleDateString(undefined, { weekday: 'short' })} ${clock(ms)}`;
}

// Spans [[start, end]] when a series was "on"; event entities give
// one short span per event (the state is the event's time).
function spansOf(id, series, from, now) {
  const out = [];
  if (isEvent(id)) {
    const seen = new Set();
    series.forEach(([, s]) => {
      const t = Date.parse(s);
      if (!isNaN(t) && t >= from && t <= now && !seen.has(t)) {
        seen.add(t);
        out.push([t, t + 30e3]);
      }
    });
    return out.sort((a, b) => a[0] - b[0]);
  }
  let on = null;
  series.forEach(([t, s]) => {
    if (s === 'on' && on === null) on = Math.max(t, from);
    else if (s !== 'on' && on !== null) {
      if (t >= from) out.push([on, Math.max(t, on + 30e3)]);
      on = null;
    }
  });
  if (on !== null) out.push([on, now]);
  return out;
}

// Pretend zone for Design Presets: a few hours of activity.
function szDemo(config) {
  const now = Date.now(), m = 60e3, kind = config.demo_state || 'quiet';
  const ticks = (list) => list.map((ago) => [now - ago * m, now - ago * m + 30e3]);
  return {
    tracks: [
      ['light', [[now - 400 * m, now - 385 * m], [now - 70 * m, now - 58 * m]]],
      ['motion', ticks([640, 610, 604, 420, 398, 395, 390, 260, 255, 130, 70, 66, 62, ...(kind === 'motion' ? [1] : [])])],
      ['door', ticks([612, 396, 258, 64, ...(kind === 'open' ? [] : [])]).concat(kind === 'open' ? [[now - 4 * m, now]] : [])],
      ['ring', ticks([397])],
    ],
    word: { quiet: 'Closed', motion: 'Motion just now', open: 'Open 4 min', tamper: 'Tamper' }[kind],
    chip: { quiet: 'Closed', motion: 'Motion', open: 'Open', tamper: 'Tampered' }[kind],
    level: { quiet: 'ok', motion: 'motion', open: 'open', tamper: 'tamper' }[kind],
    warn: kind === 'tamper' ? `Door sensor tampered with at ${clock(now - 3 * m)}` : '',
    last: kind === 'open' ? `Open since ${clock(now - 4 * m)} · motion ${clock(now - 62 * m)}` : `Closed since ${clock(now - 64 * m)} · motion ${clock(now - (kind === 'motion' ? 0 : 62) * m)} · rang ${clock(now - 397 * m)}`,
    bats: [['mdi:doorbell-video', 'Doorbell', 58], ['mdi:motion-sensor', 'Motion sensor', 100], ['mdi:door', 'Door contact', 18]],
    light: config.demo_light === false ? null : { name: 'Porch light', on: kind === 'motion' },
  };
}

export const SecurityZoneCardEditor = createFormEditor({
  schema: (config) => [
    { name: 'name', selector: { text: {} } },
    ...(config.demo
      ? []
      : [
          { name: 'door_entity', selector: { entity: { domain: 'binary_sensor' } } },
          { name: 'motion_entities', selector: { entity: { multiple: true, domain: ['binary_sensor', 'event'] } } },
          { name: 'doorbell_entity', selector: { entity: { domain: 'event' } } },
          { name: 'tamper_entities', selector: { entity: { multiple: true, domain: 'binary_sensor' } } },
          {
            type: 'expandable',
            name: '',
            title: 'Light tile (optional)',
            flatten: true,
            schema: [
              { name: 'light_entity', selector: { entity: { domain: ['light', 'switch'] } } },
              { name: 'light_name', selector: { text: {} } },
            ],
          },
          {
            type: 'expandable',
            name: '',
            title: 'Batteries (each on its own row)',
            flatten: true,
            schema: SZ_BATTERIES.flatMap((n) => [
              { name: `battery_${n}`, selector: { entity: { domain: 'sensor' } } },
              { name: `battery_${n}_name`, selector: { text: {} } },
            ]),
          },
          { name: 'alarm_entity', selector: { entity: { domain: 'alarm_control_panel' } } },
        ]),
    {
      type: 'expandable',
      name: '',
      title: 'Activity strip',
      flatten: true,
      schema: [
        { name: 'hours', selector: { select: { mode: 'dropdown', options: [{ value: '6', label: 'Last 6 hours' }, { value: '12', label: 'Last 12 hours' }, { value: '24', label: 'Last 24 hours' }] } } },
        { name: 'strip', selector: { select: { mode: 'dropdown', options: [{ value: 'bars', label: 'Bars (busy periods)' }, { value: 'ticks', label: 'Ticks (each event)' }] } } },
        { name: 'show_last', selector: { boolean: {} }, default: true },
      ],
    },
    {
      type: 'expandable',
      name: '',
      title: 'Demo mode (a pretend zone, for Design Presets)',
      flatten: true,
      schema: [
        { name: 'demo', selector: { boolean: {} } },
        {
          name: 'demo_state',
          selector: {
            select: {
              mode: 'dropdown',
              options: [
                { value: 'quiet', label: 'All quiet' },
                { value: 'motion', label: 'Motion just now' },
                { value: 'open', label: 'Door open' },
                { value: 'tamper', label: 'Tampered with' },
              ],
            },
          },
        },
      ],
    },
  ],
  labels: {
    name: 'Zone name (e.g. Front Garden)',
    door_entity: 'Door or window contact (optional)',
    motion_entities: 'Motion sensors and camera motion (optional)',
    doorbell_entity: 'Doorbell ring (optional)',
    tamper_entities: 'Tamper sensors (optional)',
    light_entity: 'Light',
    light_name: 'Light tile name (optional)',
    ...Object.fromEntries(SZ_BATTERIES.flatMap((n) => [[`battery_${n}`, `Battery ${n}`], [`battery_${n}_name`, `Battery ${n} name (e.g. Doorbell)`]])),
    alarm_entity: 'Alarm (optional: an open door turns red while it’s set)',
    hours: 'Time shown',
    strip: 'Strip style',
    show_last: 'Last events line',
    demo: 'Use a pretend zone instead of real sensors',
    demo_state: 'Pretend zone shows',
  },
  helpers: {
    motion_entities: 'Motion sensors (on/off) and camera motion events both work.',
    battery_1: 'Named rows, e.g. Doorbell 58% and Hue sensor 100%. Below 25% turns amber.',
  },
});

export class SecurityZoneCard extends HTMLElement {
  setConfig(config) {
    if (!config.demo && !config.door_entity && !(config.motion_entities || []).length && !config.doorbell_entity) {
      throw new Error('Choose a door, motion sensor or doorbell (or set demo: true)');
    }
    this.config = config;
    this._built = false;
    this._hours = Number(config.hours) || 12;
    this._demo = config.demo ? szDemo(config) : null;
    const c = config;
    this._ids = [c.door_entity, ...(c.motion_entities || []), c.doorbell_entity, ...(c.tamper_entities || []), c.light_entity].filter(Boolean);
    this._hist = this._demo ? null : new KitHistory(this, this._ids, this._hours, kitStateHistory);
    this._live = {};
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
  }

  // History plus the changes seen since it loaded.
  _series(id) {
    const hist = (this._hist && this._hist.data && this._hist.data[id]) || [];
    const lastHist = hist.length ? hist[hist.length - 1][0] : 0;
    const live = (this._live[id] || []).filter((p) => p[0] > lastHist);
    return hist.concat(live);
  }

  _watch() {
    this._ids.forEach((id) => {
      const st = this._hass.states[id];
      if (!st) return;
      const t = Date.parse(st.last_changed);
      const list = (this._live[id] = this._live[id] || []);
      const last = list[list.length - 1];
      if (!last || last[0] !== t || last[1] !== st.state) list.push([t, st.state]);
      if (list.length > 200) list.splice(0, list.length - 200);
    });
  }

  _data() {
    if (this._demo) return this._demo;
    const c = this.config, s = (id) => (id ? this._hass.states[id] : null);
    const now = Date.now(), from = now - this._hours * 3600e3;
    const tracks = [];
    if (c.light_entity) tracks.push(['light', spansOf(c.light_entity, this._series(c.light_entity), from, now)]);
    (c.motion_entities || []).forEach((id) => tracks.push(['motion', spansOf(id, this._series(id), from, now)]));
    if (c.door_entity) tracks.push(['door', spansOf(c.door_entity, this._series(c.door_entity), from, now)]);
    (c.tamper_entities || []).forEach((id) => tracks.push(['tamper', spansOf(id, this._series(id), from, now)]));
    if (c.doorbell_entity) tracks.push(['ring', spansOf(c.doorbell_entity, this._series(c.doorbell_entity), from, now)]);

    const door = s(c.door_entity);
    const open = !!door && door.state === 'on';
    const tampered = (c.tamper_entities || []).map(s).filter((st) => st && st.state === 'on');
    const alarm = s(c.alarm_entity);
    const armed = !!alarm && /^armed/.test(alarm.state);
    // Latest motion and ring times, from the current states and the history.
    const lastOf = (id) => {
      const st = s(id);
      if (!st) return null;
      if (isEvent(id)) {
        const t = Date.parse(st.state);
        return isNaN(t) ? null : t;
      }
      if (st.state === 'on') return now;
      const ons = spansOf(id, this._series(id), 0, now);
      return ons.length ? ons[ons.length - 1][0] : null;
    };
    const motionAt = Math.max(0, ...(c.motion_entities || []).map(lastOf).filter(Boolean));
    const ringAt = c.doorbell_entity ? lastOf(c.doorbell_entity) : null;
    const moving = motionAt && now - motionAt < RECENT;
    const ringing = ringAt && now - ringAt < RECENT;
    const ids = [c.door_entity, ...(c.motion_entities || []), c.doorbell_entity].filter(Boolean);
    const unavailable = ids.length && ids.every((id) => !s(id) || s(id).state === 'unavailable');

    let level = 'ok', word = door ? 'Closed' : 'Quiet', chip = door ? 'Closed' : 'Quiet', warn = '';
    if (moving) [level, word, chip] = ['motion', 'Motion just now', 'Motion'];
    if (ringing) [level, word, chip] = ['motion', 'Doorbell just now', 'Doorbell'];
    if (open) {
      const mins = Math.max(0, Math.round((now - Date.parse(door.last_changed)) / 60e3));
      [level, word, chip] = [armed ? 'tamper' : 'open', armed ? 'Open while armed' : mins < 1 ? 'Just opened' : `Open ${mins} min`, 'Open'];
    }
    if (tampered.length) {
      level = 'tamper';
      word = 'Tamper';
      chip = 'Tampered';
      warn = `${tampered.map((st) => st.attributes.friendly_name || st.entity_id).join(', ')} tampered with at ${clock(Date.parse(tampered[0].last_changed))}`;
    }
    if (unavailable) [level, word, chip] = ['off', 'Unavailable', 'Unavailable'];

    const last = [];
    if (door && door.state !== 'unavailable') last.push(`${open ? 'Open' : 'Closed'} since ${when(Date.parse(door.last_changed))}`);
    if (motionAt) last.push(`${last.length ? 'motion' : 'Motion'} ${now - motionAt < RECENT ? 'just now' : when(motionAt)}`);
    if (ringAt) last.push(`${last.length ? 'rang' : 'Rang'} ${when(ringAt)}`);

    const bats = SZ_BATTERIES.filter((n) => c[`battery_${n}`]).map((n) => {
      const st = s(c[`battery_${n}`]);
      const name = c[`battery_${n}_name`] || (st && String(st.attributes.friendly_name || '').replace(/\s*battery$/i, '')) || c[`battery_${n}`];
      return [(st && st.attributes.icon) || 'mdi:battery', name, kitNum(st)];
    });
    const light = c.light_entity && s(c.light_entity) ? { name: c.light_name || s(c.light_entity).attributes.friendly_name || c.light_entity, on: s(c.light_entity).state === 'on' } : null;
    return { tracks, word, chip, level, warn, last: last.join(' · '), bats, light };
  }

  _render() {
    if (!this._hass) return;
    const c = this.config;
    if (this._compact) return kitCompact(this, this._compactSpec());
    if (!this._built) {
      this.innerHTML = kitShell(
        `
        <div class="sz-warn" style="display:none; align-items:center; gap:8px; padding:8px 10px; border-radius:10px; background:${KIT_COLOR.bad}; color:#fff; font-weight:600; font-size:0.85rem;"></div>
        <div style="display:flex; flex-direction:column; gap:4px;">
          <div style="display:flex; align-items:center; gap:8px;">
            <div class="sz-strip"></div>
            <span class="ck-chip sz-chip"></span>
          </div>
          <div class="sz-axis ck-sub" style="display:flex; justify-content:space-between; font-size:0.66rem; font-variant-numeric:tabular-nums; margin-right:var(--sz-chip-w, 0px);"></div>
        </div>
        <div class="sz-last ck-sub" style="font-size:0.76rem;"></div>
        <div class="sz-bats" style="display:flex; flex-direction:column; gap:3px;"></div>
        <div class="ck-row sz-light"></div>`,
        `.sz-strip { position:relative; flex:1; min-width:0; height:20px; border-radius:6px; background:rgba(127,127,127,.12); overflow:hidden; touch-action:pan-y; user-select:none; -webkit-user-select:none; -webkit-touch-callout:none; }
        .sz-strip i { position:absolute; top:3px; bottom:3px; min-width:2px; border-radius:2px; }
        .sz-strip b { position:absolute; bottom:2px; border-radius:2px 2px 0 0; }
        .sz-strip .sz-grid { position:absolute; top:0; bottom:0; width:1px; background:rgba(127,127,127,.18); }
        .sz-strip .sz-cursor { position:absolute; top:0; bottom:0; width:2px; margin-left:-1px; background:var(--primary-text-color); opacity:.8; display:none; pointer-events:none; }
        .sz-brow { display:flex; align-items:center; gap:8px; font-size:0.8rem; color:var(--secondary-text-color); font-variant-numeric:tabular-nums; }
        .sz-brow .sz-nm { flex:1; min-width:0; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
        .sz-brow .sz-bar { width:56px; height:5px; border-radius:99px; background:rgba(127,127,127,.22); overflow:hidden; flex:none; }
        .sz-brow .sz-bar i { display:block; height:100%; border-radius:inherit; }
        .sz-brow b { width:38px; text-align:right; font-weight:600; color:var(--primary-text-color); }
        .sz-brow.sz-low, .sz-brow.sz-low b { color:${KIT_COLOR.fair}; }`,
      );
      this._stripEl = this.querySelector('.sz-strip');
      this._scrub(this._stripEl);
      this._built = true;
    }
    if (!this._demo) {
      this._watch();
      if (this._hist.due()) this._hist.load(this._hass);
    }
    const d = this._data();
    const colour = { tamper: KIT_COLOR.bad, open: KIT_COLOR.fair, off: KIT_COLOR.off }[d.level] || SZ_COLOR.zone;
    const tint = { tamper: 18, open: 10 }[d.level] || 0;
    this._word = d.word + (this._demo ? ' · demo' : '');
    if (!this._scrubbing) kitHead(this, c.name || 'Zone', this._word, colour, tint);
    else kitHead(this, c.name || 'Zone', this.querySelector('.ck-word').textContent, colour, tint);

    const warn = this.querySelector('.sz-warn');
    warn.style.display = d.warn ? 'flex' : 'none';
    if (d.warn) warn.innerHTML = `${iconHtml('mdi:alert', { size: '20px' })}<span>${kitEsc(d.warn)}</span>`;

    const chip = this.querySelector('.sz-chip');
    const chipCol = d.level === 'motion' ? (d.chip === 'Doorbell' ? SZ_COLOR.ring : SZ_COLOR.zone) : d.level === 'ok' && d.chip === 'Closed' ? KIT_COLOR.good : d.level === 'ok' || d.level === 'off' ? null : colour;
    chip.textContent = d.chip;
    chip.style.color = d.level === 'motion' ? '#fff' : chipCol || 'var(--secondary-text-color)';
    chip.style.background = d.level === 'motion' ? chipCol : chipCol ? `color-mix(in srgb, ${chipCol} 22%, transparent)` : 'rgba(127,127,127,.2)';

    this._tracks = d.tracks;
    this._drawStrip(d.tracks);

    const lastEl = this.querySelector('.sz-last');
    lastEl.style.display = c.show_last !== false && d.last ? 'block' : 'none';
    lastEl.textContent = d.last;

    const batsEl = this.querySelector('.sz-bats');
    const bsig = JSON.stringify(d.bats);
    if (batsEl._sig !== bsig) {
      batsEl._sig = bsig;
      batsEl.style.display = d.bats.length ? 'flex' : 'none';
      batsEl.innerHTML = d.bats
        .map(([icon, name, v]) => {
          const low = v != null && v < LOW;
          return `<div class="sz-brow${low ? ' sz-low' : ''}">${iconHtml(v == null ? 'mdi:battery-unknown' : low ? 'mdi:battery-alert' : icon, { size: '17px', style: 'flex:none;' })}<span class="sz-nm">${kitEsc(name)}</span><span class="sz-bar"><i style="width:${v == null ? 0 : Math.max(2, Math.min(100, v))}%; background:${low ? KIT_COLOR.fair : KIT_COLOR.good};"></i></span><b>${v == null ? '–' : `${Math.round(v)}%`}</b></div>`;
        })
        .join('');
    }

    const tiles = d.light ? [{ key: 'light', name: `${d.light.name} ${d.light.on ? 'on' : 'off'}`, icon: d.light.on ? 'mdi:lightbulb-on' : 'mdi:lightbulb-outline', color: KIT_COLOR.warm, on: d.light.on }] : [];
    kitTiles(this.querySelector('.sz-light'), tiles, () => this._toggleLight());
    hydrateIcons(this);
  }

  // One row: zone name and state, a low battery if any, and the light.
  _compactSpec() {
    if (!this._demo) this._watch();
    const d = this._data();
    const colour = { tamper: KIT_COLOR.bad, open: KIT_COLOR.fair, off: KIT_COLOR.off }[d.level] || SZ_COLOR.zone;
    const low = d.bats.filter(([, , v]) => v != null && v < LOW).map(([, name, v]) => `${name} ${Math.round(v)}%`);
    return {
      name: this.config.name || 'Zone',
      color: colour,
      status: [d.word, ...low].join(' · '),
      buttons: d.light ? [{ key: 'light', icon: d.light.on ? 'mdi:lightbulb-on' : 'mdi:lightbulb-outline', title: `${d.light.name} ${d.light.on ? 'on' : 'off'}`, on: d.light.on, color: KIT_COLOR.warm }] : [],
      onButton: () => this._toggleLight(),
    };
  }

  _drawStrip(tracks) {
    const now = Date.now(), span = this._hours * 3600e3, from = now - span;
    const style = this.config.strip === 'ticks' ? 'ticks' : 'bars';
    const x = (t) => ((t - from) / span) * 100;
    const minute = Math.floor(now / 60e3);
    const sig = JSON.stringify([style, this._hours, minute, tracks.map(([k, s]) => [k, s.length, s.length ? s[s.length - 1] : 0])]);
    if (this._stripSig === sig) return;
    this._stripSig = sig;
    let html = [1, 2, 3].map((g) => `<span class="sz-grid" style="left:${g * 25}%"></span>`).join('');
    // Light on-time is a faint band behind everything.
    tracks.filter(([k]) => k === 'light').forEach(([, spans]) => spans.forEach(([a, b]) => {
      html += `<i style="left:${Math.max(0, x(a))}%; width:${Math.max(0.4, x(b) - Math.max(0, x(a)))}%; top:0; bottom:0; border-radius:0; background:${SZ_COLOR.light}; opacity:.35;"></i>`;
    }));
    if (style === 'ticks') {
      tracks.filter(([k]) => k !== 'light').forEach(([kind, spans]) => spans.forEach(([a, b]) => {
        const l = Math.max(0, x(a));
        html += `<i style="left:${l}%; width:${Math.max(0, x(b) - l)}%; background:${SZ_COLOR[kind]};"></i>`;
      }));
    } else {
      const slots = 24, slot = span / slots;
      const counts = Array.from({ length: slots }, () => ({}));
      tracks.filter(([k]) => k !== 'light').forEach(([kind, spans]) => spans.forEach(([a]) => {
        if (a < from) return;
        const k = Math.min(slots - 1, Math.floor((a - from) / slot));
        counts[k][kind] = (counts[k][kind] || 0) + 1;
      }));
      const totals = counts.map((c) => Object.values(c).reduce((p, q) => p + q, 0));
      const max = Math.max(1, ...totals);
      counts.forEach((c, k) => {
        if (!totals[k]) return;
        const kind = c.tamper ? 'tamper' : c.ring ? 'ring' : c.door ? 'door' : 'motion';
        html += `<b style="left:${(k / slots) * 100 + 0.4}%; width:${100 / slots - 0.8}%; height:${Math.max(20, (totals[k] / max) * 85)}%; background:${SZ_COLOR[kind]};"></b>`;
      });
    }
    this._stripEl.innerHTML = `${html}<span class="sz-cursor"></span>`;
    const chip = this.querySelector('.sz-chip');
    this.querySelector('.sz-axis').style.setProperty('--sz-chip-w', `${(chip.offsetWidth || 0) + 8}px`);
    this.querySelector('.sz-axis').innerHTML = [0, 0.25, 0.5, 0.75].map((f) => `<span>${clock(from + f * span)}</span>`).join('') + '<span>now</span>';
  }

  // What happened at a point on the strip, shown in the title line.
  _at(frac) {
    const span = this._hours * 3600e3, now = Date.now(), t = now - span + frac * span, tol = span / 90;
    const found = new Set();
    (this._tracks || []).forEach(([kind, spans]) => spans.forEach(([a, b]) => {
      if (t >= a - tol && t <= b + tol) found.add(SZ_WORD[kind]);
    }));
    return `${clock(t)} · ${found.size ? [...found].join(', ') : 'nothing'}`;
  }

  // Hover with a mouse, or press and hold (0.3s) then drag with a finger.
  _scrub(el) {
    const word = () => this.querySelector('.ck-word');
    const show = (clientX) => {
      const r = el.getBoundingClientRect();
      const f = Math.max(0, Math.min(1, (clientX - r.left) / (r.width || 1)));
      const cur = el.querySelector('.sz-cursor');
      if (cur) {
        cur.style.left = `${f * 100}%`;
        cur.style.display = 'block';
      }
      this._scrubbing = true;
      word().textContent = this._at(f);
    };
    const hide = () => {
      this._scrubbing = false;
      const cur = el.querySelector('.sz-cursor');
      if (cur) cur.style.display = 'none';
      word().textContent = this._word || '';
    };
    let active = false, timer = null, sx = 0, sy = 0;
    el.addEventListener('pointermove', (ev) => {
      if (ev.pointerType === 'mouse' || active) return show(ev.clientX);
      if (timer && (Math.abs(ev.clientX - sx) > 10 || Math.abs(ev.clientY - sy) > 10)) {
        clearTimeout(timer);
        timer = null;
      }
    });
    el.addEventListener('pointerleave', (ev) => ev.pointerType === 'mouse' && hide());
    el.addEventListener('pointerdown', (ev) => {
      if (ev.pointerType === 'mouse') return;
      sx = ev.clientX;
      sy = ev.clientY;
      clearTimeout(timer);
      timer = setTimeout(() => {
        timer = null;
        active = true;
        show(sx);
      }, 300);
    });
    const end = () => {
      clearTimeout(timer);
      timer = null;
      if (active) {
        active = false;
        hide();
      }
    };
    ['pointerup', 'pointercancel'].forEach((e) => el.addEventListener(e, end));
    el.addEventListener('touchmove', (ev) => {
      if (active && ev.cancelable) ev.preventDefault();
      if (active && ev.touches[0]) show(ev.touches[0].clientX);
    }, { passive: false });
    el.addEventListener('touchend', end);
    el.addEventListener('contextmenu', (ev) => active && ev.preventDefault());
  }

  _toggleLight() {
    if (this._demo) {
      if (this._demo.light) this._demo.light.on = !this._demo.light.on;
      this._render();
      return;
    }
    const id = this.config.light_entity;
    if (id) this._hass.callService('homeassistant', 'toggle', { entity_id: id });
  }

  getCardSize() {
    return 3;
  }

  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`security-zone-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { demo: true, name: 'Front Garden', hours: '12', strip: 'bars' };
  }
}

kitCompactable(SecurityZoneCard, (card) => {
  card._built = false;
  card._stripSig = null;
});

export function registerSecurityZoneCard() {
  if (!customElements.get(`security-zone-card-editor${SUFFIX}`)) customElements.define(`security-zone-card-editor${SUFFIX}`, SecurityZoneCardEditor);
  if (!customElements.get(`security-zone-card${SUFFIX}`)) customElements.define(`security-zone-card${SUFFIX}`, SecurityZoneCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `security-zone-card${SUFFIX}`,
    name: `Security Zone Card${LABEL}`,
    description: 'One zone (garden, entrance, driveway…): motion, doors, doorbell and light over the last hours, with named batteries',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
