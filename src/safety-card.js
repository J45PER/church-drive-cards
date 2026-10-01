// Safety Card: every smoke, heat and carbon monoxide alarm in one list.
// Alarms are found by themselves (binary sensors of class smoke, heat or
// carbon_monoxide), or chosen in the editor. Each row shows all clear or
// ALARM (with since when), its battery and last check-in when the alarm's
// device has them, and the CO alarm its reading in ppm. Any alarm going off
// moves to the top in red.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitShell, kitHead, kitEsc } from './card-kit.js';

const CLASSES = ['smoke', 'heat', 'carbon_monoxide'];
const ICON = { smoke: 'mdi:smoke-detector-variant', heat: 'mdi:fire', carbon_monoxide: 'mdi:molecule-co' };
const RED = '#e53935';
const GREEN = '#4caf50';

const SF_CSS = `
  .sf-row { display:flex; align-items:center; gap:10px; padding:7px 2px; cursor:pointer; }
  .sf-row + .sf-row { border-top:1px solid var(--divider-color, rgba(127,127,127,0.18)); }
  .sf-ico { flex:none; width:36px; height:36px; border-radius:10px; display:flex; align-items:center; justify-content:center; }
  .sf-body { flex:1; min-width:0; }
  .sf-name { font-weight:600; font-size:0.92rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .sf-sub { font-size:0.78rem; color:var(--secondary-text-color); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .sf-ppm { flex:none; font-size:1.25rem; font-weight:600; }
  .sf-ppm small { font-size:0.7rem; color:var(--secondary-text-color); font-weight:500; }
`;

const pad = (n) => String(n).padStart(2, '0');
const hm = (iso) => {
  const d = new Date(iso);
  return isNaN(d) ? '' : `${pad(d.getHours())}:${pad(d.getMinutes())}`;
};
const day = (iso) => {
  const d = new Date(iso);
  return isNaN(d) ? '' : d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
};

export function sfAlarms(hass, chosen) {
  const s = (hass && hass.states) || {};
  const ids = chosen && chosen.length ? chosen.filter((id) => s[id]) : Object.keys(s).filter((id) => id.startsWith('binary_sensor.') && CLASSES.includes(s[id].attributes.device_class));
  return ids;
}

function sfDemo() {
  const now = Date.now();
  return [
    { id: 'a', name: 'Entrance smoke alarm', cls: 'smoke', on: false, battery: 96, report: new Date(now - 10 * 86400000).toISOString() },
    { id: 'b', name: 'Middle floor smoke alarm', cls: 'smoke', on: false, battery: 94 },
    { id: 'c', name: "Hayley's landing smoke alarm", cls: 'smoke', on: false, battery: 95 },
    { id: 'd', name: 'Kitchen heat alarm', cls: 'heat', on: false, battery: 97 },
    { id: 'e', name: 'Carbon monoxide', cls: 'carbon_monoxide', on: false, ppm: 0 },
  ];
}

export const SafetyCardEditor = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'entities', selector: { entity: { domain: 'binary_sensor', multiple: true } } },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: { title: 'Title (optional)', entities: 'Alarms (empty for every smoke, heat and CO alarm)', demo: 'Show pretend alarms (for Design Presets)' },
  helpers: { entities: "Each alarm's battery, last check-in and CO reading are found on its device." },
});

export class SafetyCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
    this._sig = null;
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
  }

  // A sensor on the same device as `id` that matches `test`.
  _sibling(id, test) {
    const reg = this._hass.entities || {};
    const dev = reg[id] && reg[id].device_id;
    if (!dev) return null;
    const s = this._hass.states;
    return Object.keys(reg).find((x) => reg[x].device_id === dev && s[x] && test(x, s[x])) || null;
  }

  _items() {
    if (this.config.demo) return sfDemo();
    const s = this._hass.states;
    const reg = this._hass.entities || {};
    const devices = this._hass.devices || {};
    return sfAlarms(this._hass, this.config.entities).map((id) => {
      const st = s[id];
      const dev = reg[id] && devices[reg[id].device_id];
      const name = String((dev && (dev.name_by_user || dev.name)) || st.attributes.friendly_name || id).replace(/ alarm status$/i, '');
      const bat = this._sibling(id, (x, v) => x.startsWith('sensor.') && v.attributes.device_class === 'battery' && !/_plus/.test(x)) || this._sibling(id, (x, v) => x.startsWith('sensor.') && v.attributes.device_class === 'battery');
      const report = this._sibling(id, (x) => /report_time$/.test(x));
      const ppm = st.attributes.device_class === 'carbon_monoxide' ? this._sibling(id, (x, v) => x.startsWith('sensor.') && v.attributes.unit_of_measurement === 'ppm') : null;
      return {
        id,
        name,
        cls: st.attributes.device_class,
        on: st.state === 'on',
        since: st.last_changed,
        unavailable: st.state === 'unavailable',
        battery: bat && !isNaN(Number(s[bat].state)) ? Math.round(Number(s[bat].state)) : null,
        report: report ? s[report].state : null,
        ppm: ppm && !isNaN(Number(s[ppm].state)) ? Number(s[ppm].state) : null,
      };
    });
  }

  _render() {
    if (!this._hass && !this.config.demo) return;
    const c = this.config;
    if (!this._built) {
      this.innerHTML = kitShell(`<div class="sf-list"></div>`, SF_CSS);
      this._list = this.querySelector('.sf-list');
      this._list.addEventListener('click', (ev) => {
        const row = ev.target.closest('[data-id]');
        if (row && !c.demo) this.dispatchEvent(new CustomEvent('hass-more-info', { detail: { entityId: row.dataset.id }, bubbles: true, composed: true }));
      });
      this._built = true;
    }
    const items = this._items().sort((a, b) => Number(b.on) - Number(a.on));
    const alarming = items.filter((i) => i.on);
    this.querySelector('.ck-headrow').style.display = c.title ? '' : 'none';
    if (c.title) kitHead(this, c.title, alarming.length ? 'ALARM' : 'All clear', alarming.length ? RED : GREEN);
    const sig = JSON.stringify(items);
    if (sig === this._sig) return;
    this._sig = sig;
    this._list.innerHTML = items.length
      ? items
          .map((i) => {
            const col = i.on ? RED : i.unavailable ? '#9aa0ad' : GREEN;
            const bits = i.on
              ? [`ALARM since ${hm(i.since)}`]
              : [i.unavailable ? 'Not responding' : 'All clear', i.battery != null ? `battery ${i.battery}%` : '', i.report && day(i.report) ? `checked in ${day(i.report)}` : ''];
            return `<div class="sf-row" data-id="${kitEsc(i.id)}" role="button" tabindex="0" aria-label="${kitEsc(i.name)}">
              <div class="sf-ico" style="background:${i.on ? RED : `color-mix(in srgb, ${col} 20%, transparent)`}; color:${i.on ? '#fff' : col};">${iconHtml(ICON[i.cls] || 'mdi:alarm-light', { size: '19px' })}</div>
              <div class="sf-body"><div class="sf-name">${kitEsc(i.name)}</div><div class="sf-sub" style="${i.on ? `color:${RED}; font-weight:700;` : ''}">${kitEsc(bits.filter(Boolean).join(' · '))}</div></div>
              ${i.ppm != null ? `<div class="sf-ppm" style="${i.on ? `color:${RED};` : ''}">${i.ppm}<small> ppm</small></div>` : ''}
            </div>`;
          })
          .join('')
      : `<div class="ck-sub">No smoke, heat or CO alarms found.</div>`;
    hydrateIcons(this);
  }

  getCardSize() {
    return 4;
  }

  getGridOptions() {
    return { columns: 'full', rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`safety-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return {};
  }
}

export function registerSafetyCard() {
  if (!customElements.get(`safety-card-editor${SUFFIX}`)) customElements.define(`safety-card-editor${SUFFIX}`, SafetyCardEditor);
  if (!customElements.get(`safety-card${SUFFIX}`)) customElements.define(`safety-card${SUFFIX}`, SafetyCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `safety-card${SUFFIX}`,
    name: `Safety Card${LABEL}`,
    description: 'Every smoke, heat and carbon monoxide alarm: all clear or ALARM, battery, last check-in and the CO reading',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
