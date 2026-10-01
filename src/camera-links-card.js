// Camera Links Card (Manager): which cameras record when something happens,
// per alarm mode, like Ring's Linked Devices but for any motion sensor, door
// or doorbell. Rows are triggers, columns are cameras; a tick means "record
// this camera for N seconds". Tabs for Disarmed, Home (and night) and Away
// (and while the alarm's going off); the current mode is marked "now".
//
// Kept and carried out by the Church Drive integration (events.py): it turns
// on ring-mqtt's live stream for the linked cameras, which Ring saves as a
// recording, and those show in the camera's events as "Linked". Each camera
// records a linked clip at most once per the cooldown. Only administrators
// can change links.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitEsc } from './card-kit.js';

const MODES = [
  ['disarmed', 'Disarmed', 'mdi:shield-off-outline', '#4caf50'],
  ['home', 'Home', 'mdi:shield-home', '#42a5f5'],
  ['away', 'Away', 'mdi:shield-lock', '#ef5350'],
];
const SECS = [10, 20, 30, 45, 60];
const COOLDOWNS = [[60, '1 min'], [120, '2 min'], [300, '5 min'], [600, '10 min']];
const TRIGGER_CLASSES = ['motion', 'occupancy', 'presence', 'door', 'opening', 'window', 'garage_door'];

const CL_CSS = `
  .cl-tabs { display:flex; gap:6px; margin-bottom:12px; flex-wrap:wrap; }
  .cl-tab { position:relative; border:none; cursor:pointer; font:inherit; font-size:0.85rem; font-weight:700; border-radius:999px; padding:7px 14px 7px 10px; display:flex; align-items:center; gap:6px;
    background:rgba(127,127,127,0.16); color:var(--primary-text-color); }
  .cl-tab.on { color:#fff; }
  .cl-now { font-size:0.62rem; font-weight:800; text-transform:uppercase; letter-spacing:.04em; border-radius:999px; padding:1px 6px; background:rgba(255,255,255,0.28); }
  .cl-wrap { overflow-x:auto; }
  .cl-table { width:100%; border-collapse:separate; border-spacing:0 6px; font-size:0.85rem; }
  .cl-table th { font-size:0.72rem; color:var(--secondary-text-color); font-weight:600; padding:0 4px; text-align:center; white-space:nowrap; }
  .cl-table th:first-child { text-align:left; }
  .cl-table td { background:rgba(127,127,127,0.1); padding:8px 6px; text-align:center; }
  .cl-table td:first-child { text-align:left; border-radius:10px 0 0 10px; min-width:120px; }
  .cl-table td:last-child { border-radius:0 10px 10px 0; white-space:nowrap; }
  .cl-nm b { display:block; font-size:0.86rem; font-weight:600; }
  .cl-nm small { color:var(--secondary-text-color); font-size:0.72rem; }
  .cl-tk { width:22px; height:22px; border-radius:6px; border:none; cursor:pointer; background:rgba(127,127,127,0.28); color:#fff; display:inline-flex; align-items:center; justify-content:center; padding:0; }
  .cl-tk.on { background:#26a69a; }
  .cl-tk.self { background:color-mix(in srgb, #26a69a 35%, transparent); cursor:default; }
  .cl-tk[disabled]:not(.self) { cursor:default; opacity:.7; }
  .cl-secs, .cl-add, .cl-cool { height:30px; border-radius:8px; border:1px solid var(--divider-color, rgba(127,127,127,0.3)); background:var(--secondary-background-color, rgba(127,127,127,0.12));
    color:var(--primary-text-color); font:inherit; font-size:0.8rem; padding:0 6px; }
  .cl-x { border:none; background:transparent; color:var(--secondary-text-color); cursor:pointer; padding:2px; vertical-align:middle; }
  .cl-foot { display:flex; flex-wrap:wrap; gap:10px 16px; align-items:center; margin-top:10px; font-size:0.8rem; color:var(--secondary-text-color); }
  .cl-foot select.cl-add { flex:1 1 220px; height:36px; border-style:dashed; }
  .cl-copy { border:none; cursor:pointer; font:inherit; font-size:0.78rem; font-weight:600; border-radius:999px; padding:6px 12px; background:rgba(127,127,127,0.16); color:var(--primary-text-color); }
  .cl-note { margin-top:10px; font-size:0.75rem; color:var(--secondary-text-color); line-height:1.4; }
`;

export const CameraLinksCardEditor = createFormEditor({
  schema: () => [{ name: 'demo', selector: { boolean: {} } }],
  labels: { demo: 'Show pretend links (for Design Presets)' },
  helpers: { demo: 'Rows are what triggers; tick the cameras that should record, separately for Disarmed, Home and Away.' },
});

export class CameraLinksCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
    this._data = null;
    this._tab = null;
  }

  set hass(hass) {
    this._hass = hass;
    if (this.config.demo) {
      if (!this._data) this._data = this._demo();
    } else {
      // Reload when the alarm mode changes (to move the "now" marker).
      const alarms = Object.keys(hass.states).filter((id) => id.startsWith('alarm_control_panel.')).map((id) => hass.states[id].state).join();
      if (alarms !== this._alarms) {
        this._alarms = alarms;
        this._load();
      }
    }
    this._render();
  }

  _demo() {
    const link = { 'event.front_door_motion': { cams: ['driveway'], secs: 30 }, 'event.front_door_ding': { cams: ['driveway', 'entrance'], secs: 30 } };
    return {
      mode: 'away',
      cooldown: 120,
      cameras: [
        { base: 'front_door', name: 'Front Door', battery: true },
        { base: 'driveway', name: 'Driveway', battery: true },
        { base: 'garden', name: 'Garden', battery: true },
        { base: 'entrance', name: 'Entrance', battery: false },
        { base: 'living_room', name: 'Living Room', battery: false },
      ],
      links: { disarmed: { 'event.front_door_ding': link['event.front_door_ding'] }, home: { ...link }, away: { ...link, 'binary_sensor.front_door': { cams: ['entrance'], secs: 20 } } },
    };
  }

  async _load() {
    try {
      this._data = await this._hass.connection.sendMessagePromise({ type: 'church_drive/camera/links' });
      this._sig = null;
      this._render();
    } catch (err) {
      this._failed = true;
      this._render();
    }
  }

  _admin() {
    return !!(this.config.demo || (this._hass && this._hass.user && this._hass.user.is_admin));
  }

  _name(id) {
    const st = this._hass && this._hass.states[id];
    if (st) return String(st.attributes.friendly_name || id);
    return id.replace(/^[a-z_]+\./, '').replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase());
  }

  _kind(id) {
    if (/_ding$/.test(id)) return 'Doorbell pressed';
    if (/^event\..*_motion$/.test(id)) return 'Camera motion';
    const dc = this._hass && this._hass.states[id] && this._hass.states[id].attributes.device_class;
    if (['door', 'opening', 'window', 'garage_door'].includes(dc)) return 'Opens';
    return 'Motion sensor';
  }

  // Every row: each camera's own motion and doorbell, plus anything linked in any mode.
  _rows() {
    const d = this._data;
    const s = (this._hass && this._hass.states) || {};
    const rows = [];
    d.cameras.forEach((c) => {
      [`event.${c.base}_motion`, `event.${c.base}_ding`].forEach((id) => (this.config.demo ? id.endsWith('_motion') || c.base === 'front_door' : s[id]) && rows.push(id));
    });
    MODES.forEach(([m]) => Object.keys(d.links[m] || {}).forEach((id) => !rows.includes(id) && rows.push(id)));
    return rows;
  }

  async _set(mode, trigger, cams, secs) {
    const d = this._data;
    const table = (d.links[mode] = d.links[mode] || {});
    if (cams === null) delete table[trigger];
    else table[trigger] = { cams, secs: secs || (table[trigger] && table[trigger].secs) || 30 };
    this._sig = null;
    this._render();
    if (this.config.demo) return;
    try {
      const msg = { type: 'church_drive/camera/links/set', mode, trigger };
      if (cams !== null) {
        msg.cams = cams;
        msg.secs = table[trigger].secs;
      }
      this._data = await this._hass.connection.sendMessagePromise(msg);
    } catch (err) {
      this._load();
      return;
    }
    this._sig = null;
    this._render();
  }

  _render() {
    if (!this._hass && !this.config.demo) return;
    if (!this._built) {
      this.innerHTML = `<style>${CL_CSS}</style><div class="cl-tabs"></div><div class="cl-wrap"></div><div class="cl-foot"></div><div class="cl-note"></div>`;
      this.addEventListener('click', (ev) => this._click(ev));
      this.addEventListener('change', (ev) => this._change(ev));
      this._built = true;
    }
    const d = this._data;
    if (!d) {
      this.querySelector('.cl-wrap').innerHTML = `<div class="cl-note">${this._failed ? 'Camera links need the latest Church Drive integration (and a restart).' : 'Loading…'}</div>`;
      return;
    }
    if (!this._tab) this._tab = d.mode || 'disarmed';
    const admin = this._admin();
    const sig = JSON.stringify([d, this._tab, admin]);
    if (sig === this._sig) return;
    this._sig = sig;
    const tab = this._tab;
    const table = d.links[tab] || {};

    this.querySelector('.cl-tabs').innerHTML = MODES.map(
      ([m, label, icon, colour]) =>
        `<button class="cl-tab${m === tab ? ' on' : ''}" data-tab="${m}" style="${m === tab ? `background:${colour};` : ''}">${iconHtml(icon, { size: '17px' })}${label}${m === d.mode ? '<span class="cl-now">now</span>' : ''}</button>`,
    ).join('');

    const rows = this._rows();
    const head = `<tr><th>When this triggers</th>${d.cameras.map((c) => `<th>${kitEsc(c.name)}</th>`).join('')}<th>Record</th>${admin ? '<th></th>' : ''}</tr>`;
    const body = rows
      .map((id) => {
        const rule = table[id];
        const cams = (rule && rule.cams) || [];
        const own = (id.match(/^event\.([a-z0-9_]+?)_(motion|ding)$/) || [])[1];
        const cells = d.cameras
          .map((c) => {
            if (c.base === own) return `<td><span class="cl-tk self" title="Records its own motion">${iconHtml('mdi:check', { size: '15px' })}</span></td>`;
            const on = cams.includes(c.base);
            return `<td><button class="cl-tk${on ? ' on' : ''}" data-tick="${kitEsc(c.base)}" data-trigger="${kitEsc(id)}" ${admin ? '' : 'disabled'} aria-label="${kitEsc(c.name)}" aria-pressed="${on}">${on ? iconHtml('mdi:check', { size: '15px' }) : ''}</button></td>`;
          })
          .join('');
        const secs = cams.length
          ? admin
            ? `<select class="cl-secs" data-secs="${kitEsc(id)}">${SECS.map((n) => `<option value="${n}"${n === (rule.secs || 30) ? ' selected' : ''}>${n} s</option>`).join('')}</select>`
            : `${rule.secs || 30} s`
          : '—';
        const removable = admin && !own;
        return `<tr><td class="cl-nm"><b>${kitEsc(this._name(id))}</b><small>${this._kind(id)}</small></td>${cells}<td>${secs}</td>${admin ? `<td>${removable ? `<button class="cl-x" data-remove="${kitEsc(id)}" aria-label="Remove">${iconHtml('mdi:close', { size: '16px' })}</button>` : ''}</td>` : ''}</tr>`;
      })
      .join('');
    this.querySelector('.cl-wrap').innerHTML = `<table class="cl-table">${head}${body}</table>`;

    const foot = this.querySelector('.cl-foot');
    if (admin) {
      const s = (this._hass && this._hass.states) || {};
      const used = new Set(rows);
      const choices = this.config.demo
        ? [['binary_sensor.front_door', 'Front Door'], ['binary_sensor.back_door', 'Back Door'], ['binary_sensor.garden_sensor_motion', 'Garden sensor motion']]
        : Object.keys(s)
            .filter((id) => !used.has(id) && ((id.startsWith('binary_sensor.') && TRIGGER_CLASSES.includes(s[id].attributes.device_class)) || /^event\..*_(motion|ding)$/.test(id)))
            .map((id) => [id, this._name(id)])
            .sort((a, b) => a[1].localeCompare(b[1]));
      const label = MODES.find((m) => m[0] === tab)[1];
      foot.innerHTML = `<select class="cl-add" aria-label="Add a trigger"><option value="">+ Add a trigger (any motion sensor, door or doorbell)</option>${choices.map(([id, n]) => `<option value="${kitEsc(id)}">${kitEsc(n)}</option>`).join('')}</select>
        <button class="cl-copy" data-copy="1">Use these for all modes</button>
        <span>Each camera at most once every <select class="cl-cool">${COOLDOWNS.map(([v, t]) => `<option value="${v}"${v === Number(d.cooldown) ? ' selected' : ''}>${t}</option>`).join('')}</select></span>`;
      foot.dataset.label = label;
    } else {
      foot.innerHTML = '';
    }
    const battery = d.cameras.filter((c) => c.battery).map((c) => c.name);
    this.querySelector('.cl-note').textContent = `Linked recordings show in that camera's events as "Linked". ${battery.length ? `Battery cameras (${battery.join(', ')}) use more battery for each one. ` : ''}A camera doesn't send its own motion alerts while it's recording a linked clip. If Ring's own Linked Devices are still on in the Ring app, turn them off there so cameras don't record twice.`;
    hydrateIcons(this);
  }

  _click(ev) {
    const t = ev.target.closest('[data-tab]');
    if (t) {
      this._tab = t.dataset.tab;
      this._sig = null;
      return this._render();
    }
    if (!this._admin() || !this._data) return;
    const tick = ev.target.closest('[data-tick]');
    if (tick) {
      const id = tick.dataset.trigger;
      const rule = (this._data.links[this._tab] || {})[id] || { cams: [], secs: 30 };
      const cams = rule.cams.includes(tick.dataset.tick) ? rule.cams.filter((c) => c !== tick.dataset.tick) : [...rule.cams, tick.dataset.tick];
      return this._set(this._tab, id, cams, rule.secs);
    }
    const rm = ev.target.closest('[data-remove]');
    if (rm) {
      // Off in every mode, so the row goes.
      MODES.forEach(([m]) => (this._data.links[m] || {})[rm.dataset.remove] && this._set(m, rm.dataset.remove, null));
      return;
    }
    if (ev.target.closest('[data-copy]')) {
      const from = this._data.links[this._tab] || {};
      MODES.forEach(([m]) => {
        if (m === this._tab) return;
        const to = this._data.links[m] || {};
        Object.keys(to).forEach((id) => !(id in from) && this._set(m, id, null));
        Object.entries(from).forEach(([id, r]) => this._set(m, id, [...r.cams], r.secs));
      });
    }
  }

  _change(ev) {
    if (!this._admin() || !this._data) return;
    const el = ev.target;
    if (el.matches('.cl-secs')) {
      const id = el.dataset.secs;
      const rule = (this._data.links[this._tab] || {})[id];
      if (rule) this._set(this._tab, id, rule.cams, Number(el.value));
    } else if (el.matches('.cl-add') && el.value) {
      this._set(this._tab, el.value, [], 30);
    } else if (el.matches('.cl-cool')) {
      this._data.cooldown = Number(el.value);
      if (!this.config.demo) this._hass.connection.sendMessagePromise({ type: 'church_drive/camera/settings', cooldown: Number(el.value) }).catch(() => {});
    }
  }

  getCardSize() {
    return 5;
  }

  getGridOptions() {
    return { columns: 'full', rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`camera-links-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return {};
  }
}

export function registerCameraLinksCard() {
  if (!customElements.get(`camera-links-card-editor${SUFFIX}`)) customElements.define(`camera-links-card-editor${SUFFIX}`, CameraLinksCardEditor);
  if (!customElements.get(`camera-links-card${SUFFIX}`)) customElements.define(`camera-links-card${SUFFIX}`, CameraLinksCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `camera-links-card${SUFFIX}`,
    name: `Camera Links Card${LABEL}`,
    description: 'Which cameras record when a sensor, door or doorbell triggers, for Disarmed, Home and Away',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
