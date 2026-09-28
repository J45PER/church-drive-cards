// Device Health Card: every device Church Drive watches (see the
// integration's health.py), with whether it's responding, when it was last
// heard from and how often it usually reports. Stale devices show why, their
// last real reading, the fixes tried so far, and a Fix now button (fixes are automatic anyway).
// The title turns amber while anything needs attention.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { KIT_COLOR, kitShell, kitHead, kitRealText, HEALTH_SENSOR, kitCompact, kitCompactable } from './card-kit.js';

const DH_ICONS = {
  fan: 'mdi:fan',
  climate: 'mdi:thermostat',
  cover: 'mdi:blinds-horizontal',
  light: 'mdi:lightbulb',
  sensor: 'mdi:gauge',
  binary_sensor: 'mdi:checkbox-blank-circle-outline',
  alarm_control_panel: 'mdi:shield-home',
};

function dhDemo() {
  const t = (mins) => new Date(Date.now() - mins * 60e3).toISOString();
  return {
    'fan.demo_fan': { name: 'Bedroom Fan', status: 'stale', reason: 'Came back with old readings after a restart', since: t(34), last_heard: t(34), usual_gap: 194, last_real: { state: 'on', preset_mode: 'speed_1', at: t(53) }, fixes: ['19:32 Refreshed', '19:33 Re-synced to its 19:11 reading'] },
    'fan.demo_purifier': { name: 'Air Purifier', status: 'ok', last_heard: t(1), usual_gap: 194 },
    'climate.demo_downstairs': { name: 'Downstairs', status: 'ok', last_heard: t(4), usual_gap: 900 },
    'sensor.demo_co': { name: 'Carbon Monoxide Alarm CO Reading', status: 'ok', last_heard: t(180) },
  };
}

const ago = (iso) => {
  if (!iso) return 'not heard yet';
  const m = Math.round((Date.now() - Date.parse(iso)) / 60e3);
  if (m < 1) return 'just now';
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  return h < 48 ? `${h} h ago` : `${Math.round(h / 24)} days ago`;
};
const every = (s) => (!s ? '' : s < 90 ? `every ${Math.round(s)} s` : s < 5400 ? `every ${Math.round(s / 60)} min` : `every ${Math.round(s / 3600)} h`);

export const DeviceHealthCardEditor = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'show_ok', selector: { boolean: {} }, default: true },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: {
    title: 'Title (optional)',
    show_ok: 'List devices that are fine too',
    demo: 'Show pretend devices (for Design Presets)',
  },
  helpers: {
    title: 'Choose the devices to watch in Settings → Devices & services → Church Drive → Configure → Device health.',
  },
});

export class DeviceHealthCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
  }

  _render() {
    if (!this._hass) return;
    const c = this.config;
    if (this._compact) {
      const sensor = this._hass.states[HEALTH_SENSOR];
      const devices = c.demo ? dhDemo() : (sensor && sensor.attributes.devices) || {};
      const bad = Object.keys(devices).filter((id) => devices[id].status !== 'ok');
      return kitCompact(this, {
        name: c.title || 'Device Health',
        color: bad.length ? KIT_COLOR.fair : KIT_COLOR.good,
        status: bad.length ? bad.map((id) => devices[id].name || id).join(', ') : 'All responding',
      });
    }
    if (!this._built) {
      this.innerHTML = kitShell(`<div class="dh-list" style="display:flex; flex-direction:column;"></div>`);
      this._list = this.querySelector('.dh-list');
      this._built = true;
    }
    const sensor = this._hass.states[HEALTH_SENSOR];
    const devices = c.demo ? dhDemo() : (sensor && sensor.attributes.devices) || {};
    const ids = Object.keys(devices);
    const bad = ids.filter((id) => devices[id].status !== 'ok');
    const colour = !c.demo && !sensor ? KIT_COLOR.off : bad.length ? KIT_COLOR.fair : KIT_COLOR.good;
    const word = !c.demo && !sensor ? 'Not set up' : bad.length ? `${bad.length} need${bad.length === 1 ? 's' : ''} attention` : ids.length ? 'All responding' : 'Nothing watched';
    kitHead(this, c.title || 'Device Health', word + (c.demo ? ' · demo' : ''), colour);

    const minute = Math.floor(Date.now() / 60e3);
    const sig = JSON.stringify([devices, minute, c.show_ok]);
    if (sig === this._sig) return;
    this._sig = sig;
    if (!c.demo && !sensor) {
      this._list.innerHTML = `<div class="ck-sub" style="line-height:1.5;">Device health isn't running. Update Church Drive, then choose devices to watch in Settings → Devices &amp; services → Church Drive → Configure → Device health.</div>`;
      return;
    }
    if (!ids.length) {
      this._list.innerHTML = `<div class="ck-sub" style="line-height:1.5;">No devices watched yet. Choose them in Settings → Devices &amp; services → Church Drive → Configure → Device health.</div>`;
      return;
    }
    const order = [...bad, ...ids.filter((id) => devices[id].status === 'ok')].filter((id) => c.show_ok !== false || devices[id].status !== 'ok');
    this._list.innerHTML = order
      .map((id, i) => {
        const d = devices[id];
        const ok = d.status === 'ok';
        const col = ok ? KIT_COLOR.good : KIT_COLOR.fair;
        return `<div class="dh-row" data-id="${id}" style="display:flex; flex-direction:column; gap:6px; padding:9px 0;${i ? ' border-top:1px solid var(--divider-color, rgba(127,127,127,0.22));' : ''}">
          <div style="display:flex; align-items:center; gap:10px;">
            ${iconHtml(DH_ICONS[id.split('.')[0]] || 'mdi:devices', { size: '22px', style: `color:${col}; flex:none;` })}
            <div style="flex:1; min-width:0;">
              <div class="dh-name" style="font-size:0.95rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></div>
              <div class="dh-sub ck-sub" style="font-size:0.74rem; line-height:1.35;"></div>
            </div>
            <span class="ck-chip" style="color:${col}; background:color-mix(in srgb, ${col} 20%, transparent);">${ok ? 'OK' : 'Stale'}</span>
          </div>
          ${ok ? '' : `<div class="dh-fixes ck-sub" style="font-size:0.74rem; padding-left:32px;"></div>
          <div style="display:flex; gap:6px; padding-left:32px;">
            <button class="dh-fix" type="button" style="border:none; border-radius:10px; padding:7px 10px; font:inherit; font-size:0.8rem; font-weight:600; background:#ffa726; color:#2a1700; cursor:pointer;">Fix now</button>
          </div>`}
        </div>`;
      })
      .join('');
    this._list.querySelectorAll('.dh-row').forEach((el) => {
      const id = el.dataset.id;
      const d = devices[id];
      el.querySelector('.dh-name').textContent = d.name || id;
      el.querySelector('.dh-sub').textContent =
        d.status === 'ok'
          ? [`Heard ${ago(d.last_heard)}`, d.usual_gap ? `usually ${every(d.usual_gap)}` : ''].filter(Boolean).join(' · ')
          : [d.reason, d.last_real ? `last real: ${kitRealText(d.last_real)}` : '', `heard ${ago(d.last_heard)}`].filter(Boolean).join(' · ');
      const fixes = el.querySelector('.dh-fixes');
      if (fixes) fixes.textContent = (d.fixes || []).length ? `Tried: ${d.fixes.join(' · ')}` : 'Fixing automatically…';
      const call = (action) => !c.demo && this._hass.callService('church_drive', 'health_fix', { entity_id: id, action });
      const fix = el.querySelector('.dh-fix');
      if (fix) fix.addEventListener('click', () => call(id.startsWith('fan.') ? 'nudge' : 'resync'));
    });
    hydrateIcons(this);
  }

  getCardSize() {
    return 4;
  }

  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`device-health-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return {};
  }
}

kitCompactable(DeviceHealthCard, (card) => {
  card._built = false;
  card._sig = null;
});

export function registerDeviceHealthCard() {
  if (!customElements.get(`device-health-card-editor${SUFFIX}`)) customElements.define(`device-health-card-editor${SUFFIX}`, DeviceHealthCardEditor);
  if (!customElements.get(`device-health-card${SUFFIX}`)) customElements.define(`device-health-card${SUFFIX}`, DeviceHealthCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `device-health-card${SUFFIX}`,
    name: `Device Health Card${LABEL}`,
    description: 'Watched devices: responding or stale, last heard, and fixes',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
