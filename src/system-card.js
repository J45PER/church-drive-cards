// System Card ("Devices & services"): is the house's internet up, can Home
// Assistant be reached from outside, when were the backups, are there any
// updates, and how long has Home Assistant been running.
//
// Everything is found by itself (the eero's internet status, Home Assistant
// Cloud's remote connection, the backup sensors, the uptime sensor and every
// update.* entity); each can be set in the editor instead. Everyone sees the
// internet row; the rest are for administrators only.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitShell, kitHead, kitEsc } from './card-kit.js';

const GOOD = '#4caf50';
const WARN = '#ffa726';
const BAD = '#e53935';

const SC_CSS = `
  .sc-row { display:flex; align-items:center; gap:10px; padding:7px 2px; }
  .sc-row + .sc-row { border-top:1px solid var(--divider-color, rgba(127,127,127,0.18)); }
  .sc-ico { flex:none; width:36px; height:36px; border-radius:10px; display:flex; align-items:center; justify-content:center; background:rgba(127,127,127,0.16); }
  .sc-body { flex:1; min-width:0; }
  .sc-name { font-weight:600; font-size:0.92rem; }
  .sc-sub { font-size:0.78rem; color:var(--secondary-text-color); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .sc-pill { flex:none; border-radius:999px; padding:2px 9px; font-size:0.72rem; font-weight:700; }
  .sc-tap { cursor:pointer; }
`;

const pad = (n) => String(n).padStart(2, '0');
function when(iso, now = new Date()) {
  const d = new Date(iso);
  if (isNaN(d)) return '';
  const day = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const diff = Math.round((day - today) / 86400000);
  const t = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  if (diff === 0) return `${t} today`;
  if (diff === -1) return `${t} yesterday`;
  if (diff === 1) return `${t} tomorrow`;
  return `${d.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' })} ${t}`;
}

// The entities the card uses: the config's, else found by themselves.
export function scFind(hass, c = {}) {
  const s = (hass && hass.states) || {};
  const reg = (hass && hass.entities) || {};
  const ids = Object.keys(s);
  const pick = (id) => (id && s[id] ? id : null);
  const internet =
    pick(c.internet) ||
    ids.find((id) => id.startsWith('binary_sensor.') && reg[id] && reg[id].platform === 'eero' && s[id].attributes.device_class === 'connectivity') ||
    ids.find((id) => id.startsWith('binary_sensor.') && /wan|internet/.test(id) && s[id].attributes.device_class === 'connectivity') ||
    null;
  return {
    internet,
    remote: pick(c.remote) || pick('binary_sensor.remote_ui'),
    backup_last: pick(c.backup_last) || ids.find((id) => /^sensor\.backup_last_successful/.test(id)) || null,
    backup_next: pick(c.backup_next) || ids.find((id) => /^sensor\.backup_next_scheduled/.test(id)) || null,
    uptime: pick(c.uptime) || pick('sensor.uptime'),
    updates: ids.filter((id) => id.startsWith('update.')),
  };
}

// Overall status for a panel's colour and summary: 'ok', 'warn' or 'bad'.
export function scStatus(hass, c = {}, admin = true) {
  const f = scFind(hass, c);
  const s = hass.states;
  if (f.internet && s[f.internet].state === 'off') return { level: 'bad', text: 'Internet down' };
  if (!admin) return { level: 'ok', text: f.internet ? 'Internet OK' : '' };
  if (f.remote && s[f.remote].state === 'off') return { level: 'warn', text: 'Remote access down' };
  const last = f.backup_last && new Date(s[f.backup_last].state);
  if (last && !isNaN(last) && Date.now() - last > 2 * 86400000) return { level: 'warn', text: 'No recent backup' };
  const ups = f.updates.filter((id) => s[id].state === 'on').length;
  if (ups) return { level: 'warn', text: `${ups} update${ups === 1 ? '' : 's'}` };
  return { level: 'ok', text: 'All OK' };
}

export const SystemCardEditor = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'internet', selector: { entity: { domain: 'binary_sensor' } } },
    { name: 'remote', selector: { entity: { domain: 'binary_sensor' } } },
    { name: 'backup_last', selector: { entity: { domain: 'sensor' } } },
    { name: 'backup_next', selector: { entity: { domain: 'sensor' } } },
    { name: 'uptime', selector: { entity: { domain: 'sensor' } } },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: {
    title: 'Title (optional)',
    internet: 'Internet connection (optional)',
    remote: 'Remote access (optional)',
    backup_last: 'Last backup (optional)',
    backup_next: 'Next backup (optional)',
    uptime: 'Home Assistant started (optional)',
    demo: 'Show pretend values (for Design Presets)',
  },
  helpers: {
    internet: "Leave these empty to use the ones found by themselves: the eero's internet status, Home Assistant Cloud, the backup sensors and the uptime sensor. Everyone sees the internet row; the rest are for administrators.",
  },
});

export class SystemCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
    this._sig = null;
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
  }

  _rows() {
    const c = this.config;
    if (c.demo) {
      return [
        { icon: 'mdi:web', name: 'Internet', sub: 'Connected · eero', pill: ['Online', GOOD] },
        { icon: 'mdi:cloud-outline', name: 'Remote access', sub: 'Home Assistant Cloud', pill: ['On', GOOD] },
        { icon: 'mdi:content-save-outline', name: 'Backups', sub: 'Last 04:49 today · next 05:37 tomorrow' },
        { icon: 'mdi:update', name: 'Updates', sub: 'Up to date' },
        { icon: 'mdi:home-assistant', name: 'Home Assistant', sub: 'Running since 09:29 today' },
      ];
    }
    const h = this._hass;
    const s = h.states;
    const admin = !!(h.user && h.user.is_admin);
    const f = scFind(h, c);
    const rows = [];
    if (f.internet) {
      const up = s[f.internet].state === 'on';
      rows.push({ icon: 'mdi:web', name: 'Internet', sub: up ? 'Connected' : `Down since ${when(s[f.internet].last_changed)}`, pill: [up ? 'Online' : 'Down', up ? GOOD : BAD], entity: f.internet });
    }
    if (!admin) return rows;
    if (f.remote) {
      const up = s[f.remote].state === 'on';
      rows.push({ icon: 'mdi:cloud-outline', name: 'Remote access', sub: 'Home Assistant Cloud', pill: [up ? 'On' : 'Down', up ? GOOD : WARN], entity: f.remote });
    }
    if (f.backup_last || f.backup_next) {
      const last = f.backup_last ? s[f.backup_last].state : '';
      const next = f.backup_next ? s[f.backup_next].state : '';
      const old = last && Date.now() - new Date(last) > 2 * 86400000;
      rows.push({
        icon: 'mdi:content-save-outline',
        name: 'Backups',
        sub: [last && when(last) ? `Last ${when(last)}` : 'No backup yet', next && when(next) ? `next ${when(next)}` : ''].filter(Boolean).join(' · '),
        pill: old ? ['Overdue', WARN] : null,
        entity: f.backup_last,
      });
    }
    const ups = f.updates.filter((id) => s[id].state === 'on');
    rows.push({
      icon: 'mdi:update',
      name: 'Updates',
      sub: ups.length ? ups.map((id) => s[id].attributes.title || s[id].attributes.friendly_name).slice(0, 3).join(', ') + (ups.length > 3 ? ` and ${ups.length - 3} more` : '') : 'Up to date',
      pill: ups.length ? [`${ups.length}`, WARN] : null,
      link: '/config/updates',
    });
    if (f.uptime) rows.push({ icon: 'mdi:home-assistant', name: 'Home Assistant', sub: `Running since ${when(s[f.uptime].state)}`, entity: f.uptime });
    return rows;
  }

  _render() {
    if (!this._hass && !this.config.demo) return;
    const c = this.config;
    if (!this._built) {
      this.innerHTML = kitShell(`<div class="sc-list"></div>`, SC_CSS);
      this._list = this.querySelector('.sc-list');
      this._list.addEventListener('click', (ev) => {
        const row = ev.target.closest('[data-entity],[data-link]');
        if (!row || c.demo) return;
        if (row.dataset.entity) this.dispatchEvent(new CustomEvent('hass-more-info', { detail: { entityId: row.dataset.entity }, bubbles: true, composed: true }));
        else {
          history.pushState(null, '', row.dataset.link);
          window.dispatchEvent(new CustomEvent('location-changed'));
        }
      });
      this._built = true;
    }
    this.querySelector('.ck-headrow').style.display = c.title ? '' : 'none';
    if (c.title) kitHead(this, c.title, '', '#4caf50');
    const rows = this._rows();
    const sig = JSON.stringify(rows);
    if (sig === this._sig) return;
    this._sig = sig;
    this._list.innerHTML = rows
      .map((r) => {
        const tint = r.pill ? r.pill[1] : null;
        const attr = r.entity ? `data-entity="${kitEsc(r.entity)}"` : r.link ? `data-link="${kitEsc(r.link)}"` : '';
        return `<div class="sc-row${attr ? ' sc-tap' : ''}" ${attr}>
          <div class="sc-ico" style="${tint && tint !== GOOD ? `background:color-mix(in srgb, ${tint} 22%, transparent); color:${tint};` : ''}">${iconHtml(r.icon, { size: '19px' })}</div>
          <div class="sc-body"><div class="sc-name">${kitEsc(r.name)}</div><div class="sc-sub">${kitEsc(r.sub)}</div></div>
          ${r.pill ? `<span class="sc-pill" style="background:color-mix(in srgb, ${r.pill[1]} 22%, transparent); color:${r.pill[1]};">${kitEsc(r.pill[0])}</span>` : ''}
        </div>`;
      })
      .join('');
    hydrateIcons(this);
  }

  getCardSize() {
    return 4;
  }

  getGridOptions() {
    return { columns: 'full', rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`system-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { title: 'Devices & services' };
  }
}

export function registerSystemCard() {
  if (!customElements.get(`system-card-editor${SUFFIX}`)) customElements.define(`system-card-editor${SUFFIX}`, SystemCardEditor);
  if (!customElements.get(`system-card${SUFFIX}`)) customElements.define(`system-card${SUFFIX}`, SystemCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `system-card${SUFFIX}`,
    name: `System Card${LABEL}`,
    description: 'Devices & services: internet, remote access, backups, updates and how long Home Assistant has been running',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
