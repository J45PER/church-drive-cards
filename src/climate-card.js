// Climate Card: one thermostat, radiator valve or air conditioner, laid out
// like the alarm card. From the top, each row can be switched off per card:
//   the name as the title in the status colour, one word of status beside it;
//   a gauge (min to max) with the target in its centre, the room as a white
//     dot and the target as a tick (for reading, not dragging), humidity and
//     outside temperature, and the room temperature big on the right;
//   a "window open" warning (from a window/door sensor);
//   history: room temperature (state colour, target dashed) and humidity
//     (purple) over 24 hours, on one graph when both are on;
//   − and + as two wide buttons;
//   full-width dropdowns: mode, preset, and on air con fan speed and swing;
//   one row (up to five) of quick settings, tiles like the light card's scenes
//     that each set a mode, preset and/or temperature;
//   extra readings as small chips.
// The card tints towards its colour while heating or cooling.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { stcColor } from './section-title-card.js';
import { SUFFIX, LABEL } from './suffix.js';

const CC_MAX_QUICK = 5;
const CC_RING = 84;
const CC_HUMIDITY = '#b388ff';
const CC_HISTORY_HOURS = 24;

const CC_MODES = {
  off: { name: 'Off', icon: 'mdi:power', color: '#8b919c', colors: ['#5d636e', '#3a3f48'] },
  heat: { name: 'Heat', icon: 'mdi:fire', color: '#ff7a2f', colors: ['#ff9a4d', '#e2531d'] },
  cool: { name: 'Cool', icon: 'mdi:snowflake', color: '#3aa0ff', colors: ['#63b3ff', '#1f6fd1'] },
  heat_cool: { name: 'Heat/Cool', icon: 'mdi:sun-snowflake-variant', color: '#ffa94d', colors: ['#ffb36b', '#3f8fe0'] },
  auto: { name: 'Auto', icon: 'mdi:thermostat-auto', color: '#a594de', colors: ['#a594de', '#6a4fb3'] },
  dry: { name: 'Dry', icon: 'mdi:water-percent', color: '#d4a72c', colors: ['#e3c35a', '#a8841b'] },
  fan_only: { name: 'Fan', icon: 'mdi:fan', color: '#26c6da', colors: ['#4dd8e8', '#12879a'] },
};
const CC_PRESETS = {
  none: { name: 'None', icon: 'mdi:circle-off-outline', color: '#8b919c' },
  eco: { name: 'Eco', icon: 'mdi:leaf', color: '#4caf50', colors: ['#6fcf73', '#2e7d32'] },
  boost: { name: 'Boost', icon: 'mdi:rocket-launch', color: '#e53935', colors: ['#ff7b7b', '#c62828'] },
  away: { name: 'Away', icon: 'mdi:home-export-outline', color: '#90a4ae', colors: ['#90a4ae', '#546e7a'] },
  sleep: { name: 'Sleep', icon: 'mdi:power-sleep', color: '#7e6fd6', colors: ['#8a7de0', '#3b2f86'] },
  comfort: { name: 'Comfort', icon: 'mdi:sofa', color: '#ffa94d', colors: ['#ffb36b', '#d9731f'] },
  home: { name: 'Home', icon: 'mdi:home', color: '#64b5f6', colors: ['#7fb3d5', '#3b6e99'] },
};
const CC_COLOR = { heating: '#ff7a2f', cooling: '#3aa0ff', drying: '#d4a72c', fan: '#26c6da', eco: '#4caf50', off: '#8b919c' };

// Rows and whether a new card shows them.
const CC_ROWS = {
  show_controls: true,
  show_mode: true,
  show_preset: true,
  show_fan: true,
  show_swing: true,
  show_quick: true,
  show_temperature_history: false,
  show_humidity_history: false,
};
const row = (config, key) => (config[key] != null ? !!config[key] : CC_ROWS[key]);

const cap = (text) => String(text || '').replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase());
const noPreset = (p) => p == null || p === '' || p === 'none';
const deg = (n) => `${Number(n).toFixed(1)}°`;
const esc = (text) => String(text).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);

// A pretend thermostat for the Design Presets page (demo: true). Nothing is
// sent to Home Assistant; buttons change this state and redraw.
function ccDemoState(config) {
  const off = config.demo_mode === 'off';
  return {
    entity_id: 'climate.demo',
    state: off ? 'off' : 'heat',
    attributes: {
      friendly_name: 'Downstairs',
      hvac_modes: ['off', 'heat'],
      preset_modes: ['none', 'eco'],
      preset_mode: 'none',
      current_temperature: 20.2,
      temperature: off ? null : 21.5,
      current_humidity: 57,
      hvac_action: off ? 'off' : 'heating',
      min_temp: 10,
      max_temp: 32,
      target_temp_step: 0.5,
    },
  };
}
function ccDemoHistory() {
  const now = Date.now();
  const temps = [19.1, 18.9, 18.8, 18.7, 18.9, 19.6, 20.4, 20.9, 20.6, 20.1, 19.9, 20.2, 20.6, 20.9, 21.1, 20.8, 20.3, 19.8, 19.5, 19.7, 20.0, 20.2];
  const hums = [59, 60, 60, 61, 61, 60, 58, 56, 55, 56, 57, 58, 59, 62, 66, 63, 60, 59, 58, 57, 57, 57];
  const at = (i) => now - (CC_HISTORY_HOURS * 3600e3 * (temps.length - 1 - i)) / (temps.length - 1);
  return {
    temperature: temps.map((v, i) => [at(i), v]),
    humidity: hums.map((v, i) => [at(i), v]),
    target: temps.map((_, i) => [at(i), 21.5]),
  };
}

// Default quick settings. With the mode dropdown shown, favourite
// temperatures in the device's main mode (plus Eco); without it, the modes
// and presets themselves.
export function ccDefaultQuick(st, config = {}) {
  const a = (st && st.attributes) || {};
  const modes = (a.hvac_modes || []).filter((m) => CC_MODES[m]);
  const presets = (a.preset_modes || []).filter((p) => !noPreset(p));
  // Air con (anything that cools) gets cooling favourites.
  const main = modes.includes('cool') ? 'cool' : modes.includes('heat') ? 'heat' : null;
  if (row(config, 'show_mode') && main) {
    const temps = main === 'heat' ? [18, 20, 21] : [24, 22, 20];
    const names = main === 'heat' ? ['Night', 'Day', 'Comfort'] : ['Sleep', 'Cool', 'Cold'];
    const icons = main === 'heat' ? ['mdi:weather-night', 'mdi:sofa', 'mdi:fire'] : ['mdi:weather-night', 'mdi:snowflake', 'mdi:snowflake-alert'];
    const out = temps.map((t, i) => ({ name: `${names[i]} ${t}°`, icon: icons[i], hvac_mode: main, temperature: t }));
    if (presets.includes('eco')) out.push({ name: 'Eco', preset_mode: 'eco' });
    return out.slice(0, CC_MAX_QUICK);
  }
  const ordered = [...modes.filter((m) => m === 'off'), ...modes.filter((m) => m !== 'off')];
  const out = ordered.map((m) => ({ name: CC_MODES[m].name, hvac_mode: m }));
  presets.forEach((p) => out.push({ name: (CC_PRESETS[p] || {}).name || cap(p), preset_mode: p }));
  return out.slice(0, CC_MAX_QUICK);
}

export const ClimateCardEditor = createFormEditor({
  fill: (config, hass) => {
    if (config.quick_settings !== 'reset' && config.quick_settings != null) return config;
    const st = config.demo ? ccDemoState(config) : hass && config.entity && hass.states[config.entity];
    if (!st) return config;
    return { ...config, quick_settings: ccDefaultQuick(st, config) };
  },
  buttons: [{ label: 'Reset', field: 'quick_settings', variant: 'danger', apply: (config) => ({ ...config, quick_settings: 'reset' }) }],
  alerts: [
    {
      field: 'quick_settings',
      text: (config) =>
        Array.isArray(config.quick_settings) && config.quick_settings.length > CC_MAX_QUICK
          ? `Only the first ${CC_MAX_QUICK} quick settings show (one row).`
          : '',
    },
  ],
  schema: (config, hass) => {
    const st = config.demo ? ccDemoState(config) : hass && config.entity && hass.states[config.entity];
    const a = (st && st.attributes) || {};
    const modeOptions = (a.hvac_modes && a.hvac_modes.length ? a.hvac_modes : Object.keys(CC_MODES)).map((m) => ({
      value: m,
      label: (CC_MODES[m] || {}).name || cap(m),
    }));
    const presetOptions = [{ value: 'none', label: 'None (clear the preset)' }].concat(
      (a.preset_modes || Object.keys(CC_PRESETS)).filter((p) => !noPreset(p)).map((p) => ({ value: p, label: (CC_PRESETS[p] || {}).name || cap(p) }))
    );
    const toggle = (name) => ({ name, selector: { boolean: {} }, default: CC_ROWS[name] });
    return [
      ...(config.demo ? [] : [{ name: 'entity', selector: { entity: { domain: 'climate' } } }]),
      { name: 'name', selector: { text: {} } },
      {
        type: 'expandable',
        name: '',
        title: 'Rows to show',
        flatten: true,
        schema: [
          toggle('show_temperature_history'),
          toggle('show_humidity_history'),
          toggle('show_controls'),
          toggle('show_mode'),
          toggle('show_preset'),
          toggle('show_fan'),
          toggle('show_swing'),
          toggle('show_quick'),
        ],
      },
      {
        type: 'expandable',
        name: '',
        title: 'Other sensors',
        flatten: true,
        schema: [
          { name: 'outdoor_entity', selector: { entity: { domain: ['weather', 'sensor'] } } },
          { name: 'window_entity', selector: { entity: { domain: 'binary_sensor' } } },
          { name: 'humidity_entity', selector: { entity: { domain: 'sensor' } } },
          { name: 'entities', selector: { entity: { multiple: true } } },
        ],
      },
      {
        name: 'quick_names',
        selector: {
          select: {
            mode: 'dropdown',
            options: [
              { value: 'auto', label: 'Auto (hide on small tiles)' },
              { value: 'always', label: 'Always show' },
              { value: 'never', label: 'Never show (icons only)' },
            ],
          },
        },
      },
      {
        name: 'quick_settings',
        selector: {
          object: {
            multiple: true,
            label_field: 'name',
            fields: {
              name: { label: 'Name', required: true, selector: { text: {} } },
              hvac_mode: { label: 'Mode (optional)', selector: { select: { mode: 'dropdown', options: modeOptions } } },
              preset_mode: { label: 'Preset (optional)', selector: { select: { mode: 'dropdown', options: presetOptions } } },
              temperature: {
                label: 'Temperature (optional)',
                selector: { number: { mode: 'box', min: a.min_temp || 5, max: a.max_temp || 35, step: a.target_temp_step || 0.5, unit_of_measurement: '°' } },
              },
              icon: { label: 'Icon (optional)', selector: { icon: {} } },
              color: { label: 'Colour (optional)', selector: { ui_color: {} } },
            },
          },
        },
      },
      {
        type: 'expandable',
        name: '',
        title: 'Demo mode (a pretend thermostat, for Design Presets)',
        flatten: true,
        schema: [
          { name: 'demo', selector: { boolean: {} } },
          { name: 'demo_mode', selector: { select: { mode: 'dropdown', options: [{ value: 'heat', label: 'Heating' }, { value: 'off', label: 'Off' }] } } },
        ],
      },
    ];
  },
  labels: {
    entity: 'Thermostat, valve or air conditioner',
    name: 'Title (optional)',
    show_temperature_history: 'Temperature history (24 hours)',
    show_humidity_history: 'Humidity history (24 hours)',
    show_controls: '− and + buttons',
    show_mode: 'Mode dropdown',
    show_preset: 'Preset dropdown',
    show_fan: 'Fan speed dropdown (if the device has one)',
    show_swing: 'Swing dropdown (if the device has one)',
    show_quick: 'Quick settings',
    outdoor_entity: 'Outdoor temperature (weather or sensor)',
    window_entity: 'Window or door sensor (shows a warning when open)',
    humidity_entity: 'Humidity sensor (if the thermostat has none)',
    entities: 'Extra readings (shown as small chips)',
    quick_names: 'Quick setting names',
    quick_settings: 'Quick settings (one row, up to 5)',
    demo: 'Use a pretend thermostat instead of a real one',
    demo_mode: 'Pretend thermostat starts',
  },
  helpers: {
    show_temperature_history: 'With humidity history on too, both share one graph.',
    quick_settings: 'Each sets a mode, a preset and/or a temperature. The one that matches the thermostat glows.',
  },
});

export class ClimateCard extends HTMLElement {
  setConfig(config) {
    if (!config.entity && !config.demo) throw new Error('entity required (or set demo: true)');
    this.config = config;
    this._built = false;
    this._demo = config.demo ? ccDemoState(config) : null;
    this._pending = null;
    this._history = config.demo ? ccDemoHistory() : null;
    this._historyAt = 0;
    this._open = null;
    this._sig = null;
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
  }

  disconnectedCallback() {
    this._closeMenu();
  }

  _state() {
    return this._demo || (this._hass && this._hass.states[this.config.entity]);
  }

  _build() {
    this.innerHTML = `
      <ha-card class="cc-card" style="border:none; box-shadow:0 3px 10px rgba(0,0,0,0.45); border-radius:16px; padding:16px; background:var(--card-background-color); transition:background-color .6s ease; display:flex; flex-direction:column; gap:12px;">
        <style>
          .cc-btn { position:relative; overflow:hidden; flex:1 1 0; min-width:0; height:48px; border:none; border-radius:12px; cursor:pointer;
            background:rgba(127,127,127,0.16); color:var(--primary-text-color); font:inherit; font-size:26px; line-height:1; }
          .cc-btn:disabled { opacity:.4; cursor:default; }
          .cc-btn::after, .cc-q::after, .cc-dd::after { content:''; position:absolute; inset:0; background:#fff; opacity:0; transition:opacity .15s; pointer-events:none; border-radius:inherit; }
          .cc-btn:not(:disabled):hover::after, .cc-q:hover::after, .cc-dd:hover::after { opacity:.08; }
          .cc-btn:focus-visible, .cc-q:focus-visible, .cc-dd:focus-visible, .cc-opt:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
          .cc-q { position:relative; container-type:inline-size; flex:1 1 0; min-width:0; height:48px; border:none; border-radius:12px; padding:0; overflow:hidden; cursor:pointer; color:#fff;
            transition:opacity .2s, filter .2s, transform .2s, box-shadow .2s; }
          .cc-q.cc-dim { opacity:.45; filter:saturate(.5); }
          .cc-q-name { font-size:13px; }
          @container (max-width: 89px) { .cc-names-auto .cc-q-name { display:none; } }
          .cc-names-never .cc-q-name { display:none; }
          .cc-dd { position:relative; width:100%; height:48px; border:none; border-radius:12px; cursor:pointer; background:rgba(127,127,127,0.16);
            color:var(--primary-text-color); font:inherit; display:flex; align-items:center; gap:10px; padding:0 12px; text-align:left; }
          .cc-dd-text { flex:1; min-width:0; display:flex; flex-direction:column; line-height:1.2; }
          .cc-dd-text small { font-size:0.7rem; color:var(--secondary-text-color); }
          .cc-dd-text b { font-size:0.95rem; font-weight:600; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
          .cc-menu { margin-top:4px; background:var(--card-background-color); border-radius:12px;
            box-shadow:0 3px 10px rgba(0,0,0,0.45); padding:6px; display:flex; flex-direction:column; gap:2px; max-height:280px; overflow:auto; }
          .cc-opt { border:none; background:none; color:var(--primary-text-color); font:inherit; font-size:0.95rem; text-align:left; padding:10px; border-radius:8px; cursor:pointer; display:flex; align-items:center; gap:10px; }
          .cc-opt:hover { background:rgba(127,127,127,0.14); }
          .cc-opt.cc-on { background:rgba(127,127,127,0.22); font-weight:600; }
          .cc-chip { display:inline-flex; align-items:center; gap:6px; padding:5px 10px; border-radius:999px; background:rgba(127,127,127,0.16); font-size:0.8rem; }
        </style>
        <div style="display:flex; align-items:baseline; gap:8px;">
          <div class="cc-title" style="flex:1; min-width:0; font-size:1.5rem; font-weight:500; line-height:1.2; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; transition:color .6s;"></div>
          <div class="cc-word" style="flex:none; font-size:0.85rem; color:var(--secondary-text-color);"></div>
        </div>
        <div style="display:flex; align-items:center; gap:14px;">
          <div class="cc-gauge-wrap" style="position:relative; width:${CC_RING}px; height:${CC_RING}px; flex:none; cursor:pointer;" title="More details">
            <svg class="cc-gauge" viewBox="0 0 ${CC_RING} ${CC_RING}" width="${CC_RING}" height="${CC_RING}" aria-hidden="true" style="display:block;"></svg>
            <div style="position:absolute; inset:0; display:flex; flex-direction:column; align-items:center; justify-content:center;">
              <b class="cc-target" style="font-size:1.15rem; font-weight:700; font-variant-numeric:tabular-nums;"></b>
              <span class="cc-target-label" style="font-size:0.7rem; color:var(--secondary-text-color);"></span>
            </div>
          </div>
          <div class="cc-info" style="flex:1; min-width:0; display:flex; flex-direction:column; gap:4px; font-size:0.85rem; color:var(--secondary-text-color);"></div>
          <div style="flex:none; text-align:right;">
            <div class="cc-room" style="font-size:2rem; font-weight:700; line-height:1; font-variant-numeric:tabular-nums;"></div>
            <div style="font-size:0.85rem; color:var(--secondary-text-color);">room</div>
          </div>
        </div>
        <div class="cc-window"></div>
        <div class="cc-history"></div>
        <div class="cc-controls" style="display:flex; gap:6px;">
          <button class="cc-btn cc-down" aria-label="Lower the temperature">−</button>
          <button class="cc-btn cc-up" aria-label="Raise the temperature">+</button>
        </div>
        <div class="cc-dropdowns" style="display:flex; flex-direction:column; gap:6px;"></div>
        <div class="cc-quick" style="display:flex; gap:6px;"></div>
        <div class="cc-chips" style="display:flex; flex-wrap:wrap; gap:6px;"></div>
      </ha-card>`;
    const q = (sel) => this.querySelector(sel);
    this._els = {
      card: q('.cc-card'), title: q('.cc-title'), word: q('.cc-word'), gauge: q('.cc-gauge'), target: q('.cc-target'), targetLabel: q('.cc-target-label'),
      info: q('.cc-info'), room: q('.cc-room'), window: q('.cc-window'), history: q('.cc-history'), controls: q('.cc-controls'),
      down: q('.cc-down'), up: q('.cc-up'), dropdowns: q('.cc-dropdowns'), quick: q('.cc-quick'), chips: q('.cc-chips'),
    };
    this._els.down.addEventListener('click', () => this._step(-1));
    this._els.up.addEventListener('click', () => this._step(1));
    q('.cc-gauge-wrap').addEventListener('click', () => this._moreInfo(this.config.entity));
    this._built = true;
  }

  _moreInfo(entityId) {
    if (!entityId || this._demo) return;
    this.dispatchEvent(new CustomEvent('hass-more-info', { detail: { entityId }, bubbles: true, composed: true }));
  }

  _view(st) {
    const a = st.attributes;
    const target = this._pending != null ? this._pending : a.temperature != null ? a.temperature : null;
    return { mode: st.state, a, target, action: a.hvac_action, preset: noPreset(a.preset_mode) ? null : a.preset_mode };
  }

  _status(v) {
    if (v.mode === 'off' || v.mode === 'unavailable') return { word: v.mode === 'off' ? 'Off' : 'Unavailable', color: CC_COLOR.off, tint: 0 };
    const presetWord = v.preset ? (CC_PRESETS[v.preset] || {}).name || cap(v.preset) : null;
    if (v.preset === 'eco') return { word: 'Eco', color: CC_COLOR.eco, tint: v.action === 'heating' || v.action === 'cooling' ? 10 : 0 };
    if (v.action === 'heating' || v.action === 'preheating') return { word: presetWord || 'Heating', color: CC_COLOR.heating, tint: 14 };
    if (v.action === 'cooling') return { word: presetWord || 'Cooling', color: CC_COLOR.cooling, tint: 14 };
    if (v.action === 'drying') return { word: presetWord || 'Drying', color: CC_COLOR.drying, tint: 8 };
    if (v.action === 'fan') return { word: presetWord || 'Fan', color: CC_COLOR.fan, tint: 8 };
    return { word: presetWord || 'Idle', color: (CC_MODES[v.mode] || {}).color || '#a594de', tint: 0 };
  }

  _entityState(id) {
    return id && this._hass && this._hass.states[id];
  }

  _render() {
    const st = this._state();
    if (!st) return;
    if (!this._built) this._build();
    const e = this._els;
    const cfg = this.config;
    const v = this._view(st);
    const s = this._status(v);
    const a = v.a;
    this._color = s.color;
    e.card.style.backgroundColor = s.tint ? `color-mix(in srgb, ${s.color} ${s.tint}%, var(--card-background-color))` : 'var(--card-background-color)';
    e.title.textContent = cfg.name || a.friendly_name || cfg.entity;
    e.title.style.color = s.color;
    e.word.textContent = s.word + (this._demo ? ' · demo' : '');
    const off = v.mode === 'off' || v.mode === 'unavailable';
    const hasTarget = !off && v.target != null;
    e.target.textContent = off ? 'Off' : hasTarget ? deg(v.target) : cap((CC_MODES[v.mode] || {}).name || v.mode);
    e.targetLabel.textContent = hasTarget ? 'target' : '';
    e.room.textContent = a.current_temperature != null ? deg(a.current_temperature) : '–';
    this._drawGauge(a, v, s, hasTarget);

    // Humidity and outside temperature beside the gauge.
    const humidity = this._humidity(a);
    const outdoor = this._entityState(cfg.outdoor_entity);
    const outTemp = outdoor && (outdoor.entity_id.startsWith('weather.') ? outdoor.attributes.temperature : outdoor.state);
    const infoSig = JSON.stringify([humidity, outTemp]);
    if (infoSig !== this._infoSig) {
      this._infoSig = infoSig;
      e.info.innerHTML = [
        humidity != null ? `<span style="display:flex; align-items:center; gap:4px;">${iconHtml('mdi:water-percent', { size: '18px', style: `color:${CC_HUMIDITY};` })}${Math.round(humidity)}%</span>` : '',
        outTemp != null && outTemp !== '' && !isNaN(Number(outTemp))
          ? `<span style="display:flex; align-items:center; gap:4px;">${iconHtml('mdi:weather-partly-cloudy', { size: '18px' })}Outside ${Math.round(Number(outTemp))}°</span>`
          : '',
      ].join('');
    }

    // Window open warning.
    const win = this._entityState(cfg.window_entity);
    const open = win && win.state === 'on';
    e.window.style.display = open ? 'flex' : 'none';
    if (open) {
      const verb = v.action === 'cooling' ? 'cooling' : 'heating';
      e.window.innerHTML = `<span class="cc-chip" style="background:color-mix(in srgb, #ffb300 22%, transparent); color:#ffd54f;">${iconHtml('mdi:window-open-variant', { size: '18px' })}${esc(win.attributes.friendly_name || 'Window')} open${off ? '' : ` · ${verb} wasted`}</span>`;
    }

    e.controls.style.display = row(cfg, 'show_controls') ? 'flex' : 'none';
    e.down.disabled = !hasTarget;
    e.up.disabled = !hasTarget;
    this._drawHistory(a, v);
    this._drawDropdowns(st, v);
    this._drawQuick(st, v);
    this._drawChips();
    hydrateIcons(this);
  }

  _humidity(a) {
    if (a.current_humidity != null) return Number(a.current_humidity);
    const h = this._entityState(this.config.humidity_entity);
    return h && !isNaN(Number(h.state)) ? Number(h.state) : null;
  }

  // 270° arc from min to max: faint up to the target, solid between the room
  // and the target, a tick at the target and a white dot at the room.
  _drawGauge(a, v, s, hasTarget) {
    const size = CC_RING, cx = size / 2, r = size / 2 - 7, len = 1.5 * Math.PI * r, sw = 6;
    const min = a.min_temp != null ? a.min_temp : 7, max = a.max_temp != null ? a.max_temp : 35;
    const pos = (t) => Math.max(0, Math.min(1, (t - min) / (max - min || 1)));
    const pt = (p, rr) => {
      const ang = ((135 + 270 * p) * Math.PI) / 180;
      return [cx + rr * Math.cos(ang), cx + rr * Math.sin(ang)];
    };
    const arc = (extra) => `<circle cx="${cx}" cy="${cx}" r="${r}" fill="none" stroke-width="${sw}" transform="rotate(135 ${cx} ${cx})" ${extra}></circle>`;
    let svg = arc(`stroke="rgba(127,127,127,0.28)" stroke-linecap="round" stroke-dasharray="${len} 9999"`);
    const room = a.current_temperature;
    if (hasTarget) {
      const t = pos(v.target);
      svg += arc(`stroke="${s.color}" stroke-opacity="0.35" stroke-linecap="round" stroke-dasharray="${Math.max(0.01, len * t)} 9999"`);
      if (room != null) {
        const lo = pos(Math.min(room, v.target)), hi = pos(Math.max(room, v.target));
        svg += arc(`stroke="${s.color}" stroke-dasharray="${Math.max(0.01, len * (hi - lo))} 9999" stroke-dashoffset="${-len * lo}"`);
      }
      const [x1, y1] = pt(t, r - 9), [x2, y2] = pt(t, r + 5);
      svg += `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${s.color}" stroke-width="3" stroke-linecap="round"></line>`;
    }
    if (room != null) {
      const [dx, dy] = pt(pos(room), r);
      svg += `<circle cx="${dx}" cy="${dy}" r="5" fill="#fff" stroke="var(--card-background-color)" stroke-width="2"></circle>`;
    }
    this._els.gauge.innerHTML = svg;
  }

  // ---- History: room temperature (state colour, target dashed) and humidity
  // (purple), each on its own scale, sharing one graph when both are on.
  async _loadHistory() {
    if (this._demo || this._historyLoading || !this._hass || !this._hass.callWS) return;
    this._historyLoading = true;
    try {
      const start = new Date(Date.now() - CC_HISTORY_HOURS * 3600e3).toISOString();
      const ids = [this.config.entity];
      if (this.config.humidity_entity) ids.push(this.config.humidity_entity);
      const res = await this._hass.callWS({
        type: 'history/history_during_period',
        start_time: start,
        entity_ids: ids,
        minimal_response: false,
        no_attributes: false,
        significant_changes_only: false,
      });
      const temperature = [], humidity = [], target = [];
      let attrs = {};
      (res[this.config.entity] || []).forEach((p) => {
        attrs = p.a || attrs;
        const t = (p.lu || p.lc || 0) * 1000;
        if (attrs.current_temperature != null) temperature.push([t, Number(attrs.current_temperature)]);
        if (attrs.current_humidity != null) humidity.push([t, Number(attrs.current_humidity)]);
        target.push([t, p.s === 'off' || attrs.temperature == null ? null : Number(attrs.temperature)]);
      });
      if (this.config.humidity_entity && !humidity.length) {
        (res[this.config.humidity_entity] || []).forEach((p) => {
          if (!isNaN(Number(p.s))) humidity.push([(p.lu || p.lc || 0) * 1000, Number(p.s)]);
        });
      }
      this._history = { temperature, humidity, target };
      this._historyAt = Date.now();
      this._sig = null;
      this._render();
    } catch (err) {
      this._historyAt = Date.now();
    }
    this._historyLoading = false;
  }

  _drawHistory(a, v) {
    const cfg = this.config;
    const showT = row(cfg, 'show_temperature_history');
    const showH = row(cfg, 'show_humidity_history');
    const box = this._els.history;
    if (!showT && !showH) {
      box.style.display = 'none';
      return;
    }
    box.style.display = 'block';
    if (!this._demo && Date.now() - this._historyAt > 10 * 60e3) this._loadHistory();
    const h = this._history;
    const sig = JSON.stringify([showT, showH, this._historyAt, this._color, !!h]);
    if (sig === this._historySig) return;
    this._historySig = sig;
    if (!h) {
      box.innerHTML = `<div style="height:74px; display:flex; align-items:center; justify-content:center; font-size:0.8rem; color:var(--secondary-text-color);">Loading history…</div>`;
      return;
    }
    // Carry the last reading up to now so the lines reach the right edge.
    const now = Date.now();
    const from = now - CC_HISTORY_HOURS * 3600e3;
    const extend = (pts, current) => {
      const out = pts.filter((p) => p[1] != null && !isNaN(p[1]));
      if (current != null) out.push([now, Number(current)]);
      return out;
    };
    const temps = showT ? extend(h.temperature, a.current_temperature) : [];
    const hums = showH ? extend(h.humidity, this._humidity(a)) : [];
    const W = 300, H = 56;
    const x = (t) => (Math.max(from, t) - from) / (now - from) * W;
    const pathOf = (pts, lo, hi) => {
      const y = (val) => H - 3 - ((val - lo) / (hi - lo || 1)) * (H - 6);
      return { y, d: pts.map((p, i) => `${i ? 'L' : 'M'}${x(p[0]).toFixed(1)},${y(p[1]).toFixed(1)}`).join(' ') };
    };
    let svg = '';
    const legend = [];
    if (hums.length > 1) {
      const vals = hums.map((p) => p[1]);
      const lo = Math.min(...vals) - 3, hi = Math.max(...vals) + 3;
      const p = pathOf(hums, lo, hi);
      if (!temps.length) svg += `<path d="${p.d} L${W},${H} L0,${H} Z" fill="${CC_HUMIDITY}" fill-opacity="0.16"></path>`;
      svg += `<path d="${p.d}" fill="none" stroke="${CC_HUMIDITY}" stroke-width="2" vector-effect="non-scaling-stroke"></path>`;
      legend.push(`<span style="color:${CC_HUMIDITY}">● Humidity ${Math.round(Math.min(...vals))}–${Math.round(Math.max(...vals))}%</span>`);
    }
    if (temps.length > 1) {
      const vals = temps.map((p) => p[1]);
      const targets = (h.target || []).map((p) => p[1]).filter((t) => t != null);
      if (v.target != null) targets.push(Number(v.target));
      const lo = Math.min(...vals, ...targets) - 0.5, hi = Math.max(...vals, ...targets) + 0.5;
      const p = pathOf(temps, lo, hi);
      let tgt = '';
      if (v.target != null) {
        const ty = p.y(Number(v.target)).toFixed(1);
        tgt = `<line x1="0" x2="${W}" y1="${ty}" y2="${ty}" stroke="${this._color}" stroke-dasharray="4 4" stroke-width="1.5" vector-effect="non-scaling-stroke"></line>`;
      }
      svg = `<path d="${p.d} L${W},${H} L0,${H} Z" fill="${this._color}" fill-opacity="0.16"></path>` + svg +
        `<path d="${p.d}" fill="none" stroke="${this._color}" stroke-width="2" vector-effect="non-scaling-stroke"></path>${tgt}`;
      legend.unshift(`<span style="color:${this._color}">● Temperature ${Math.min(...vals).toFixed(1)}–${Math.max(...vals).toFixed(1)}°</span>`);
    }
    if (!legend.length) {
      box.innerHTML = `<div style="height:74px; display:flex; align-items:center; justify-content:center; font-size:0.8rem; color:var(--secondary-text-color);">No history yet</div>`;
      return;
    }
    if (legend.length === 1) legend.push('<span>last 24 h</span>');
    box.innerHTML = `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" style="display:block; width:100%; height:${H}px;" role="img" aria-label="The last 24 hours">${svg}</svg>
      <div style="display:flex; justify-content:space-between; gap:8px; flex-wrap:wrap; margin-top:2px; font-size:0.8rem; color:var(--secondary-text-color);">${legend.join('')}</div>`;
  }

  // ---- Full-width dropdowns: mode, preset, fan speed, swing.
  _dropdownDefs(st, v) {
    const a = st.attributes;
    const cfg = this.config;
    const defs = [];
    if (row(cfg, 'show_mode') && (a.hvac_modes || []).length > 1) {
      defs.push({
        key: 'mode', label: 'Mode', value: v.mode,
        options: a.hvac_modes.map((m) => ({ value: m, name: (CC_MODES[m] || {}).name || cap(m), icon: (CC_MODES[m] || {}).icon || 'mdi:thermostat', color: (CC_MODES[m] || {}).color })),
        set: (val) => this._set('set_hvac_mode', { hvac_mode: val }, () => { this._demo.state = val; }),
      });
    }
    if (row(cfg, 'show_preset') && (a.preset_modes || []).length) {
      const opts = a.preset_modes.includes('none') ? a.preset_modes : ['none', ...a.preset_modes];
      defs.push({
        key: 'preset', label: 'Preset', value: noPreset(a.preset_mode) ? 'none' : a.preset_mode,
        options: opts.map((p) => ({ value: p, name: (CC_PRESETS[p] || {}).name || cap(p), icon: (CC_PRESETS[p] || {}).icon || 'mdi:tune-variant', color: (CC_PRESETS[p] || {}).color })),
        set: (val) => this._set('set_preset_mode', { preset_mode: val }, () => { this._demo.attributes.preset_mode = val; }),
      });
    }
    if (row(cfg, 'show_fan') && (a.fan_modes || []).length) {
      defs.push({
        key: 'fan', label: 'Fan speed', value: a.fan_mode,
        options: a.fan_modes.map((m) => ({ value: m, name: cap(m), icon: 'mdi:fan', color: CC_COLOR.fan })),
        set: (val) => this._set('set_fan_mode', { fan_mode: val }),
      });
    }
    if (row(cfg, 'show_swing') && (a.swing_modes || []).length) {
      defs.push({
        key: 'swing', label: 'Swing', value: a.swing_mode,
        options: a.swing_modes.map((m) => ({ value: m, name: cap(m), icon: 'mdi:arrow-oscillating', color: CC_COLOR.fan })),
        set: (val) => this._set('set_swing_mode', { swing_mode: val }),
      });
    }
    return defs;
  }

  _drawDropdowns(st, v) {
    const defs = this._dropdownDefs(st, v);
    const sig = JSON.stringify([defs.map((d) => [d.key, d.value, d.options.map((o) => o.value)]), this._open]);
    if (sig === this._ddSig) return;
    this._ddSig = sig;
    const box = this._els.dropdowns;
    box.style.display = defs.length ? 'flex' : 'none';
    box.innerHTML = '';
    defs.forEach((d) => {
      const cur = d.options.find((o) => o.value === d.value) || { name: cap(d.value || '–'), icon: 'mdi:help-circle-outline' };
      const wrap = document.createElement('div');
      wrap.style.position = 'relative';
      const btn = document.createElement('button');
      btn.className = 'cc-dd';
      btn.setAttribute('aria-haspopup', 'listbox');
      btn.setAttribute('aria-expanded', String(this._open === d.key));
      btn.innerHTML = `${iconHtml(cur.icon, { size: '22px', style: `color:${cur.color || 'var(--primary-text-color)'}; flex:none;` })}
        <span class="cc-dd-text"><small>${d.label}</small><b>${esc(cur.name)}</b></span>
        <ha-icon icon="mdi:menu-down" style="--mdc-icon-size:22px; color:var(--secondary-text-color); flex:none;"></ha-icon>`;
      btn.addEventListener('click', (ev) => {
        ev.stopPropagation();
        this._open = this._open === d.key ? null : d.key;
        this._ddSig = null;
        this._render();
        if (this._open) this._listenOutside();
      });
      wrap.appendChild(btn);
      if (this._open === d.key) {
        const menu = document.createElement('div');
        menu.className = 'cc-menu';
        menu.setAttribute('role', 'listbox');
        d.options.forEach((o) => {
          const opt = document.createElement('button');
          opt.className = `cc-opt${o.value === d.value ? ' cc-on' : ''}`;
          opt.setAttribute('role', 'option');
          opt.setAttribute('aria-selected', String(o.value === d.value));
          opt.innerHTML = `${iconHtml(o.icon, { size: '20px', style: `color:${o.color || 'var(--primary-text-color)'}; flex:none;` })}<span></span>`;
          opt.querySelector('span').textContent = o.name;
          opt.addEventListener('click', (ev) => {
            ev.stopPropagation();
            this._closeMenu();
            if (o.value !== d.value) d.set(o.value);
          });
          menu.appendChild(opt);
        });
        wrap.appendChild(menu);
      }
      box.appendChild(wrap);
    });
  }

  _listenOutside() {
    if (this._outside) return;
    this._outside = () => this._closeMenu();
    setTimeout(() => document.addEventListener('click', this._outside), 0);
  }

  _closeMenu() {
    if (this._outside) document.removeEventListener('click', this._outside);
    this._outside = null;
    if (this._open) {
      this._open = null;
      this._ddSig = null;
      if (this._built) this._render();
    }
  }

  // ---- Quick settings: one row, up to five.
  _quickList(st) {
    if (!row(this.config, 'show_quick')) return [];
    const list = Array.isArray(this.config.quick_settings) ? this.config.quick_settings : ccDefaultQuick(st, this.config);
    return list.filter((q) => q && q.name).slice(0, CC_MAX_QUICK);
  }

  _matches(q, v) {
    if (q.hvac_mode && q.hvac_mode !== v.mode) return false;
    if (!q.hvac_mode && v.mode === 'off') return false;
    if (q.preset_mode && !noPreset(q.preset_mode)) {
      if (q.preset_mode !== v.preset) return false;
    } else if (v.preset && (q.hvac_mode || q.temperature != null)) return false;
    if (q.temperature != null && Number(q.temperature) !== Number(v.target)) return false;
    return true;
  }

  _drawQuick(st, v) {
    const list = this._quickList(st);
    const names = ['always', 'never'].includes(this.config.quick_names) ? this.config.quick_names : 'auto';
    const sig = JSON.stringify([list, names, v.mode, v.preset, v.target]);
    if (sig === this._quickSig) return;
    this._quickSig = sig;
    const box = this._els.quick;
    box.className = `cc-quick cc-names-${names}`;
    box.style.display = list.length ? 'flex' : 'none';
    box.innerHTML = '';
    const active = list.findIndex((q) => this._matches(q, v));
    list.forEach((q, i) => {
      const look = (q.preset_mode && !noPreset(q.preset_mode) && CC_PRESETS[q.preset_mode]) || (q.hvac_mode && CC_MODES[q.hvac_mode]) || CC_MODES.heat;
      const colors = q.color ? [stcColor(q.color), `color-mix(in srgb, ${stcColor(q.color)} 65%, #000)`] : look.colors || CC_MODES.heat.colors;
      const icon = q.icon || look.icon;
      const tile = document.createElement('button');
      tile.className = `cc-q${active !== -1 && i !== active ? ' cc-dim' : ''}`;
      tile.title = q.name;
      tile.setAttribute('aria-label', q.name);
      tile.style.background = `linear-gradient(135deg, ${colors[0]}, ${colors[1]})`;
      if (i === active) tile.style.boxShadow = `0 0 12px 2px color-mix(in srgb, ${colors[0]} 80%, transparent)`;
      tile.innerHTML = `<div style="position:absolute; inset:0; background:rgba(0,0,0,0.18);"></div>
        <div style="position:relative; display:flex; align-items:center; justify-content:center; gap:6px; height:100%; padding:0 6px;">
          ${iconHtml(icon, { size: '22px', style: 'flex-shrink:0; filter:drop-shadow(0 1px 3px rgba(0,0,0,0.55));' })}
          <span class="cc-q-name" style="min-width:0; font-weight:600; text-shadow:0 1px 2px rgba(0,0,0,0.6); white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></span>
        </div>`;
      tile.querySelector('.cc-q-name').textContent = q.name;
      tile.addEventListener('click', () => this._applyQuick(q));
      box.appendChild(tile);
    });
  }

  // ---- Extra readings as chips; a tap opens the sensor.
  _drawChips() {
    const ids = Array.isArray(this.config.entities) ? this.config.entities : [];
    const states = ids.map((id) => this._entityState(id)).filter(Boolean);
    const sig = JSON.stringify(states.map((s) => [s.entity_id, s.state]));
    if (sig === this._chipSig) return;
    this._chipSig = sig;
    const box = this._els.chips;
    box.style.display = states.length ? 'flex' : 'none';
    box.innerHTML = '';
    states.forEach((s) => {
      const chip = document.createElement('button');
      chip.className = 'cc-chip';
      chip.style.cssText = 'border:none; color:var(--primary-text-color); font:inherit; font-size:0.8rem; cursor:pointer;';
      const text = this._hass.formatEntityState ? this._hass.formatEntityState(s) : `${s.state}${s.attributes.unit_of_measurement ? ' ' + s.attributes.unit_of_measurement : ''}`;
      const icon = s.attributes.icon || (s.attributes.device_class === 'battery' ? 'mdi:battery' : s.attributes.device_class === 'temperature' ? 'mdi:thermometer' : 'mdi:information-outline');
      chip.innerHTML = `${iconHtml(icon, { size: '16px', style: 'color:var(--secondary-text-color);' })}<span></span>`;
      chip.querySelector('span').textContent = `${s.attributes.friendly_name || s.entity_id} ${text}`;
      chip.addEventListener('click', () => this._moreInfo(s.entity_id));
      box.appendChild(chip);
    });
  }

  // ---- Changing things.
  _set(service, data, demo) {
    if (this._demo) {
      if (demo) demo();
      this._demoSettle();
      return;
    }
    this._hass.callService('climate', service, { entity_id: this.config.entity, ...data });
  }

  _demoSettle() {
    const a = this._demo.attributes;
    if (this._demo.state === 'off') {
      a.hvac_action = 'off';
    } else {
      if (a.temperature == null) a.temperature = 20;
      if (a.preset_mode === 'eco') a.temperature = 16.5;
      a.hvac_action = a.current_temperature < a.temperature ? 'heating' : 'idle';
    }
    this._render();
  }

  _applyQuick(q) {
    const st = this._state();
    const a = st.attributes;
    if (this._demo) {
      if (q.hvac_mode) this._demo.state = q.hvac_mode;
      if (q.preset_mode) this._demo.attributes.preset_mode = q.preset_mode;
      else if (q.hvac_mode || q.temperature != null) this._demo.attributes.preset_mode = 'none';
      if (q.temperature != null) this._demo.attributes.temperature = Number(q.temperature);
      this._demoSettle();
      return;
    }
    if (q.temperature != null) {
      this._set('set_temperature', { temperature: Number(q.temperature), ...(q.hvac_mode ? { hvac_mode: q.hvac_mode } : {}) });
    } else if (q.hvac_mode && q.hvac_mode !== st.state) {
      this._set('set_hvac_mode', { hvac_mode: q.hvac_mode });
    }
    if (q.preset_mode) {
      this._set('set_preset_mode', { preset_mode: q.preset_mode });
    } else if ((q.hvac_mode || q.temperature != null) && q.hvac_mode !== 'off' && !noPreset(a.preset_mode) && (a.preset_modes || []).includes('none')) {
      // A plain mode or temperature setting leaves any preset (e.g. Eco).
      this._set('set_preset_mode', { preset_mode: 'none' });
    }
  }

  // − / + change the target locally and send it once tapping stops.
  _step(dir) {
    const st = this._state();
    if (!st) return;
    const a = st.attributes;
    const current = this._pending != null ? this._pending : a.temperature;
    if (current == null) return;
    const step = Number(a.target_temp_step) || 0.5;
    const min = a.min_temp != null ? a.min_temp : 7, max = a.max_temp != null ? a.max_temp : 35;
    const next = Math.round(Math.min(max, Math.max(min, Number(current) + dir * step)) * 10) / 10;
    if (this._demo) {
      this._demo.attributes.temperature = next;
      this._demoSettle();
      return;
    }
    this._pending = next;
    this._render();
    clearTimeout(this._sendTimer);
    clearTimeout(this._clearTimer);
    this._sendTimer = setTimeout(() => {
      this._set('set_temperature', { temperature: next });
      // Keep showing the new target until HA reports it.
      this._clearTimer = setTimeout(() => {
        this._pending = null;
        this._render();
      }, 3000);
    }, 700);
  }

  getCardSize() {
    const cfg = this.config || {};
    let size = 3;
    if (row(cfg, 'show_temperature_history') || row(cfg, 'show_humidity_history')) size += 2;
    if (row(cfg, 'show_controls')) size += 1;
    if (row(cfg, 'show_mode')) size += 1;
    if (row(cfg, 'show_preset')) size += 1;
    if (row(cfg, 'show_quick')) size += 1;
    return size;
  }

  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`climate-card-editor${SUFFIX}`);
  }

  static getStubConfig(hass) {
    const first = hass && Object.keys(hass.states).find((id) => id.startsWith('climate.'));
    return first ? { entity: first } : { demo: true };
  }
}

export function registerClimateCard() {
  if (!customElements.get(`climate-card-editor${SUFFIX}`)) {
    customElements.define(`climate-card-editor${SUFFIX}`, ClimateCardEditor);
  }
  if (!customElements.get(`climate-card${SUFFIX}`)) {
    customElements.define(`climate-card${SUFFIX}`, ClimateCard);
  }
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `climate-card${SUFFIX}`,
    name: `Climate Card${LABEL}`,
    description: 'A thermostat, radiator valve or air conditioner: gauge, history, − / +, dropdowns and quick settings',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
