// Carbon Monoxide Card: a CO alarm (e.g. X-Sense) at a glance.
// Green "Normal" with the CO reading on a gauge, the device status, battery
// and last report; Test (press and hold, as it sounds the real alarm) and
// Mute buttons. If the alarm goes off, the card turns red with a warning.
// Picking the CO reading sensor finds the alarm's other entities
// automatically (same entity name prefix).

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { KIT_COLOR, kitShell, kitHead, kitGauge, kitTiles, kitCap, kitNum, kitHealthBanner, kitCompact, kitCompactable } from './card-kit.js';
import { iconFor, watchIcons } from './icon-library.js';

// Where each related entity is found, from the device's name prefix.
const CO_PARTS = {
  alarm_entity: (p) => [`binary_sensor.${p}alarm_status`, `binary_sensor.${p}carbon_monoxide`, `binary_sensor.${p}co`],
  status_entity: (p) => [`sensor.${p}device_status`, `sensor.${p}status`],
  battery_entity: (p) => [`sensor.${p}battery`, `sensor.${p}battery_level`],
  report_entity: (p) => [`sensor.${p}report_time`, `sensor.${p}last_report`],
  test_entity: (p) => [`button.${p}device_test`, `button.${p}test`, `button.${p}self_test`],
  mute_entity: (p) => [`button.${p}mute`, `button.${p}silence`],
};

function coFill(hass, readingId) {
  const obj = String(readingId || '').split('.')[1] || '';
  const prefix = obj.replace(/(co_reading|co_level|co|carbon_monoxide)$/, '');
  const out = {};
  Object.entries(CO_PARTS).forEach(([key, list]) => {
    out[key] = list(prefix).find((id) => hass.states[id]) || '';
  });
  return out;
}

function coDemo(config) {
  const alarm = config.demo_state === 'alarm';
  return { ppm: alarm ? 86 : 0, alarm, status: alarm ? 'alarm' : 'normal', battery: 100, report: new Date(Date.now() - 3 * 3600e3).toISOString() };
}

export const CoAlarmCardEditor = createFormEditor({
  fill: (config, hass) => {
    if (config.demo || !config.entity || config.alarm_entity !== undefined || !hass) return config;
    return { ...config, ...coFill(hass, config.entity) };
  },
  schema: (config) => [
    ...(config.demo ? [] : [{ name: 'entity', selector: { entity: { domain: 'sensor' } } }]),
    { name: 'name', selector: { text: {} } },
    { name: 'show_buttons', selector: { boolean: {} }, default: true },
    ...(config.demo
      ? []
      : [
          {
            type: 'expandable',
            name: '',
            title: 'Other alarm entities (found automatically)',
            flatten: true,
            schema: [
              { name: 'alarm_entity', selector: { entity: { domain: 'binary_sensor' } } },
              { name: 'status_entity', selector: { entity: { domain: 'sensor' } } },
              { name: 'battery_entity', selector: { entity: { domain: 'sensor' } } },
              { name: 'report_entity', selector: { entity: { domain: 'sensor' } } },
              { name: 'test_entity', selector: { entity: { domain: 'button' } } },
              { name: 'mute_entity', selector: { entity: { domain: 'button' } } },
            ],
          },
        ]),
    {
      type: 'expandable',
      name: '',
      title: 'Demo mode (a pretend alarm, for Design Presets)',
      flatten: true,
      schema: [
        { name: 'demo', selector: { boolean: {} } },
        { name: 'demo_state', selector: { select: { mode: 'dropdown', options: [{ value: 'normal', label: 'Normal' }, { value: 'alarm', label: 'CO detected' }] } } },
      ],
    },
  ],
  labels: {
    entity: 'CO reading sensor (ppm)',
    name: 'Title (optional)',
    show_buttons: 'Test and Mute buttons',
    alarm_entity: 'Alarm (on when CO is found)',
    status_entity: 'Device status (optional)',
    battery_entity: 'Battery (optional)',
    report_entity: 'Last report time (optional)',
    test_entity: 'Test button (optional)',
    mute_entity: 'Mute button (optional)',
    demo: 'Use a pretend alarm instead of a real one',
    demo_state: 'Pretend alarm shows',
  },
  helpers: { entity: 'Picking it fills in the rest of the alarm below.' },
});

export class CoAlarmCard extends HTMLElement {
  setConfig(config) {
    if (!config.entity && !config.demo) throw new Error('entity required (or set demo: true)');
    this.config = config;
    this._built = false;
    this._demo = config.demo ? coDemo(config) : null;
  }

  set hass(hass) {
    this._hass = hass;
    watchIcons(this, hass);
    this._render();
  }

  _data() {
    if (this._demo) return this._demo;
    const c = this.config, s = (id) => id && this._hass.states[id];
    const alarm = s(c.alarm_entity);
    const report = s(c.report_entity);
    return {
      ppm: kitNum(s(c.entity)),
      alarm: !!alarm && alarm.state === 'on',
      status: s(c.status_entity) ? s(c.status_entity).state : null,
      battery: kitNum(s(c.battery_entity)),
      report: report && report.state,
      unavailable: !s(c.entity) || s(c.entity).state === 'unavailable',
    };
  }

  _render() {
    if (!this._hass) return;
    const c = this.config;
    if (this._compact) {
      const d = this._data();
      const high = d.alarm || (d.ppm != null && d.ppm >= 50);
      const color = d.unavailable ? KIT_COLOR.off : high ? KIT_COLOR.bad : d.ppm >= 10 ? KIT_COLOR.fair : KIT_COLOR.good;
      return kitCompact(this, {
        name: c.name || 'Carbon Monoxide',
        color,
        value: d.ppm == null ? '–' : String(Math.round(d.ppm)),
        valueColor: color,
        status: `ppm · ${d.unavailable ? 'Unavailable' : d.alarm ? 'CO detected' : d.status ? kitCap(d.status) : 'Normal'}${d.battery != null && d.battery < 20 ? ` · battery ${Math.round(d.battery)}%` : ''}`,
      });
    }
    if (!this._built) {
      this.innerHTML = kitShell(`
        <div class="co-warn" style="display:none; align-items:center; gap:10px; padding:10px 12px; border-radius:12px; background:${KIT_COLOR.bad}; color:#fff; font-weight:600;"></div>
        <div style="display:flex; align-items:center; gap:14px;">
          <div class="co-gauge"></div>
          <div class="ck-info co-info"></div>
        </div>
        <div class="ck-row co-buttons"></div>`);
      this._built = true;
    }
    const d = this._data();
    const high = d.alarm || (d.ppm != null && d.ppm >= 50);
    const color = d.unavailable ? KIT_COLOR.off : high ? KIT_COLOR.bad : d.ppm >= 10 ? KIT_COLOR.fair : KIT_COLOR.good;
    const word = d.unavailable ? 'Unavailable' : d.alarm ? 'CO detected' : d.status ? kitCap(d.status) : 'Normal';
    kitHead(this, c.name || 'Carbon Monoxide', word + (this._demo ? ' · demo' : ''), color, high ? 30 : 0);
    kitHealthBanner(this, this._hass, c.entity, !!(this._demo || c.demo));

    const warn = this.querySelector('.co-warn');
    warn.style.display = high ? 'flex' : 'none';
    if (high) warn.innerHTML = `${iconHtml('mdi:alert', { size: '24px' })}<span>Carbon monoxide found. Get everyone outside and open doors and windows.</span>`;

    this.querySelector('.co-gauge').innerHTML = kitGauge(d.ppm == null ? 0 : Math.max(0.02, d.ppm / 100), color, d.ppm == null ? '–' : String(Math.round(d.ppm)), 'ppm CO');
    const when = d.report && !isNaN(Date.parse(d.report)) ? new Date(d.report) : null;
    const whenText = when ? when.toLocaleString(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '';
    this.querySelector('.co-info').innerHTML = [
      `<span>${iconHtml(high ? 'mdi:alert-circle' : 'mdi:shield-check', { size: '18px', style: `color:${color};` })}${high ? 'CO detected' : 'No CO detected'}</span>`,
      d.battery != null ? `<span>${iconHtml(d.battery < 20 ? 'mdi:battery-alert' : 'mdi:battery', { size: '18px', style: `color:${d.battery < 20 ? KIT_COLOR.bad : KIT_COLOR.good};` })}Battery ${Math.round(d.battery)}%</span>` : '',
      whenText ? `<span>${iconHtml('mdi:clock-outline', { size: '18px' })}Reported ${whenText}</span>` : '',
    ].join('');

    const buttons = c.show_buttons === false ? [] : [
      ...(this._demo || c.test_entity ? [{ key: 'test', name: 'Hold to test', icon: iconFor('co', 'test', 'mdi:bell-ring'), color: KIT_COLOR.good, hold: true }] : []),
      ...(this._demo || c.mute_entity ? [{ key: 'mute', name: 'Mute', icon: iconFor('co', 'mute', 'mdi:volume-off'), color: KIT_COLOR.off }] : []),
    ];
    kitTiles(this.querySelector('.co-buttons'), buttons, (t) => this._press(t.key));
    hydrateIcons(this);
  }

  _press(key) {
    if (this._demo) return;
    const id = key === 'test' ? this.config.test_entity : this.config.mute_entity;
    if (id) this._hass.callService('button', 'press', { entity_id: id });
  }

  getCardSize() {
    return 4;
  }

  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`co-alarm-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { demo: true };
  }
}

kitCompactable(CoAlarmCard);

export function registerCoAlarmCard() {
  if (!customElements.get(`co-alarm-card-editor${SUFFIX}`)) customElements.define(`co-alarm-card-editor${SUFFIX}`, CoAlarmCardEditor);
  if (!customElements.get(`co-alarm-card${SUFFIX}`)) customElements.define(`co-alarm-card${SUFFIX}`, CoAlarmCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `co-alarm-card${SUFFIX}`,
    name: `Carbon Monoxide Card${LABEL}`,
    description: 'A CO alarm: reading, status, battery, and hold-to-test',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
