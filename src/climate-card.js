// Work in progress (not registered in index.js yet): waiting on the user's extra
// features for the climate card before it goes on the Beta tab.

// Climate Card: one thermostat, radiator valve or air conditioner, laid out
// like the alarm card. The name is the title in the status colour with one
// word of status beside it; a gauge shows the device's range with the target
// in its centre (room = white dot, target = tick; for reading, not dragging);
// the room temperature sits big on the right; − and + are a row of two wide
// buttons; then one row (up to five) of quick settings, tiles like the light
// card's scenes that each set a mode, preset and/or temperature. The card
// tints towards its colour while heating or cooling.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { stcColor } from './section-title-card.js';
import { SUFFIX, LABEL } from './suffix.js';

const CC_MAX_QUICK = 5;
const CC_RING = 84;

const CC_MODES = {
  off: { name: 'Off', icon: 'mdi:power', colors: ['#5d636e', '#3a3f48'] },
  heat: { name: 'Heat', icon: 'mdi:fire', colors: ['#ff9a4d', '#e2531d'] },
  cool: { name: 'Cool', icon: 'mdi:snowflake', colors: ['#63b3ff', '#1f6fd1'] },
  heat_cool: { name: 'Heat/Cool', icon: 'mdi:sun-snowflake-variant', colors: ['#ffb36b', '#3f8fe0'] },
  auto: { name: 'Auto', icon: 'mdi:thermostat-auto', colors: ['#a594de', '#6a4fb3'] },
  dry: { name: 'Dry', icon: 'mdi:water-percent', colors: ['#e3c35a', '#a8841b'] },
  fan_only: { name: 'Fan', icon: 'mdi:fan', colors: ['#4dd8e8', '#12879a'] },
};
const CC_PRESETS = {
  eco: { name: 'Eco', icon: 'mdi:leaf', colors: ['#6fcf73', '#2e7d32'] },
  boost: { name: 'Boost', icon: 'mdi:rocket-launch', colors: ['#ff7b7b', '#c62828'] },
  away: { name: 'Away', icon: 'mdi:home-export-outline', colors: ['#90a4ae', '#546e7a'] },
  sleep: { name: 'Sleep', icon: 'mdi:power-sleep', colors: ['#8a7de0', '#3b2f86'] },
  comfort: { name: 'Comfort', icon: 'mdi:sofa', colors: ['#ffb36b', '#d9731f'] },
  home: { name: 'Home', icon: 'mdi:home', colors: ['#7fb3d5', '#3b6e99'] },
};
const CC_COLOR = { heating: '#ff7a2f', cooling: '#3aa0ff', drying: '#d4a72c', fan: '#26c6da', eco: '#4caf50', off: '#8b919c' };

const cap = (text) => String(text || '').replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase());
const noPreset = (p) => p == null || p === '' || p === 'none';

// A pretend thermostat for the Design Presets page (demo: true). Nothing is
// sent to Home Assistant; buttons change this state and redraw.
function ccDemoState(config) {
  return {
    entity_id: 'climate.demo',
    state: config.demo_mode || 'heat',
    attributes: {
      friendly_name: config.name || 'Downstairs',
      hvac_modes: ['off', 'heat'],
      preset_modes: ['none', 'eco'],
      preset_mode: 'none',
      current_temperature: 20.2,
      temperature: config.demo_mode === 'off' ? null : 21.5,
      current_humidity: 57,
      hvac_action: config.demo_mode === 'off' ? 'off' : 'heating',
      min_temp: 10,
      max_temp: 32,
      target_temp_step: 0.5,
    },
  };
}

// Default quick settings: Off, then the device's other modes, then its presets.
export function ccDefaultQuick(st) {
  const a = (st && st.attributes) || {};
  const modes = (a.hvac_modes || []).filter((m) => CC_MODES[m]);
  const ordered = [...modes.filter((m) => m === 'off'), ...modes.filter((m) => m !== 'off')];
  const out = ordered.map((m) => ({ name: CC_MODES[m].name, hvac_mode: m }));
  (a.preset_modes || []).filter((p) => !noPreset(p)).forEach((p) => out.push({ name: (CC_PRESETS[p] || {}).name || cap(p), preset_mode: p }));
  return out.slice(0, CC_MAX_QUICK);
}

function ccIsDefault(list) {
  return list.every((q) => !q.temperature && !q.icon && !q.color);
}

export const ClimateCardEditor = createFormEditor({
  fill: (config, hass) => {
    if (config.demo || !config.entity || !hass) return config;
    const st = hass.states[config.entity];
    if (!st) return config;
    if (config.quick_settings === 'reset' || config.quick_settings == null) return { ...config, quick_settings: ccDefaultQuick(st) };
    return config;
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
    const st = hass && config.entity && hass.states[config.entity];
    const a = (st && st.attributes) || {};
    const modeOptions = (a.hvac_modes && a.hvac_modes.length ? a.hvac_modes : Object.keys(CC_MODES)).map((m) => ({
      value: m,
      label: (CC_MODES[m] || {}).name || cap(m),
    }));
    const presetOptions = [{ value: 'none', label: 'None (clear the preset)' }].concat(
      (a.preset_modes || Object.keys(CC_PRESETS)).filter((p) => !noPreset(p)).map((p) => ({ value: p, label: (CC_PRESETS[p] || {}).name || cap(p) }))
    );
    return [
      ...(config.demo ? [] : [{ name: 'entity', selector: { entity: { domain: 'climate' } } }]),
      { name: 'name', selector: { text: {} } },
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
      { name: 'hide_quick_settings', selector: { boolean: {} } },
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
              temperature: { label: 'Temperature (optional)', selector: { number: { mode: 'box', min: a.min_temp || 5, max: a.max_temp || 35, step: a.target_temp_step || 0.5, unit_of_measurement: '°' } } },
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
    quick_names: 'Quick setting names',
    hide_quick_settings: 'Hide quick settings',
    quick_settings: 'Quick settings (one row, up to 5)',
    demo: 'Use a pretend thermostat instead of a real one',
    demo_mode: 'Pretend thermostat starts',
  },
  helpers: {
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
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
  }

  _state() {
    return this._demo || (this._hass && this._hass.states[this.config.entity]);
  }

  _build() {
    this.innerHTML = `
      <ha-card class="cc-card" style="border:none; box-shadow:0 3px 10px rgba(0,0,0,0.45); border-radius:16px; overflow:hidden; padding:16px; background:var(--card-background-color); transition:background-color .6s ease;">
        <style>
          .cc-btn { position:relative; overflow:hidden; flex:1 1 0; min-width:0; height:48px; border:none; border-radius:12px; cursor:pointer;
            background:rgba(127,127,127,0.16); color:var(--primary-text-color); font:inherit; font-size:26px; line-height:1; }
          .cc-btn:disabled { opacity:.4; cursor:default; }
          .cc-btn::after, .cc-q::after { content:''; position:absolute; inset:0; background:#fff; opacity:0; transition:opacity .15s; pointer-events:none; }
          .cc-btn:not(:disabled):hover::after, .cc-q:hover::after { opacity:.12; }
          .cc-btn:focus-visible, .cc-q:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
          .cc-q { position:relative; container-type:inline-size; flex:1 1 0; min-width:0; height:48px; border:none; border-radius:12px; padding:0; overflow:hidden; cursor:pointer; color:#fff;
            transition:opacity .2s, filter .2s, transform .2s, box-shadow .2s; }
          .cc-q.cc-dim { opacity:.45; filter:saturate(.5); }
          .cc-q-name { font-size:13px; }
          @container (max-width: 89px) { .cc-names-auto .cc-q-name { display:none; } }
          .cc-names-never .cc-q-name { display:none; }
        </style>
        <div style="display:flex; align-items:baseline; gap:8px; padding:0 0 10px 0;">
          <div class="cc-title" style="flex:1; min-width:0; font-size:1.5rem; font-weight:500; line-height:1.2; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; transition:color .6s;"></div>
          <div class="cc-word" style="flex:none; font-size:0.85rem; color:var(--secondary-text-color);"></div>
        </div>
        <div style="display:flex; align-items:center; gap:14px;">
          <div style="position:relative; width:${CC_RING}px; height:${CC_RING}px; flex:none;">
            <svg class="cc-gauge" viewBox="0 0 ${CC_RING} ${CC_RING}" width="${CC_RING}" height="${CC_RING}" aria-hidden="true" style="display:block;"></svg>
            <div style="position:absolute; inset:0; display:flex; flex-direction:column; align-items:center; justify-content:center;">
              <b class="cc-target" style="font-size:1.15rem; font-weight:700; font-variant-numeric:tabular-nums;"></b>
              <span class="cc-target-label" style="font-size:0.7rem; color:var(--secondary-text-color);"></span>
            </div>
          </div>
          <div class="cc-extra" style="flex:1; min-width:0; font-size:0.85rem; color:var(--secondary-text-color);"></div>
          <div style="flex:none; text-align:right;">
            <div class="cc-room" style="font-size:2rem; font-weight:700; line-height:1; font-variant-numeric:tabular-nums;"></div>
            <div style="font-size:0.85rem; color:var(--secondary-text-color);">room</div>
          </div>
        </div>
        <div style="display:flex; gap:6px; margin-top:12px;">
          <button class="cc-btn cc-down" aria-label="Lower the temperature">−</button>
          <button class="cc-btn cc-up" aria-label="Raise the temperature">+</button>
        </div>
        <div class="cc-quick" style="display:flex; gap:6px; margin-top:6px;"></div>
      </ha-card>`;
    const q = (sel) => this.querySelector(sel);
    this._els = {
      card: q('.cc-card'), title: q('.cc-title'), word: q('.cc-word'), gauge: q('.cc-gauge'), target: q('.cc-target'),
      targetLabel: q('.cc-target-label'), extra: q('.cc-extra'), room: q('.cc-room'), down: q('.cc-down'), up: q('.cc-up'), quick: q('.cc-quick'),
    };
    this._els.down.addEventListener('click', () => this._step(-1));
    this._els.up.addEventListener('click', () => this._step(1));
    this._built = true;
  }

  // What the card shows: target (the local one while − / + are being tapped).
  _view(st) {
    const a = st.attributes;
    const target = this._pending != null ? this._pending : a.temperature != null ? a.temperature : null;
    return { mode: st.state, a, target, action: a.hvac_action, preset: noPreset(a.preset_mode) ? null : a.preset_mode };
  }

  _status(v) {
    if (v.mode === 'off' || v.mode === 'unavailable') return { word: v.mode === 'off' ? 'Off' : 'Unavailable', color: CC_COLOR.off, tint: 0 };
    const word = v.preset ? (CC_PRESETS[v.preset] || {}).name || cap(v.preset) : cap(v.action || v.mode);
    if (v.preset === 'eco') return { word, color: CC_COLOR.eco, tint: v.action === 'heating' || v.action === 'cooling' ? 10 : 0 };
    if (v.action === 'heating' || v.action === 'preheating') return { word: v.preset ? word : 'Heating', color: CC_COLOR.heating, tint: 14 };
    if (v.action === 'cooling') return { word: v.preset ? word : 'Cooling', color: CC_COLOR.cooling, tint: 14 };
    if (v.action === 'drying') return { word, color: CC_COLOR.drying, tint: 8 };
    if (v.action === 'fan') return { word, color: CC_COLOR.fan, tint: 8 };
    const modeColor = { heat: CC_COLOR.heating, cool: CC_COLOR.cooling, dry: CC_COLOR.drying, fan_only: CC_COLOR.fan }[v.mode] || '#a594de';
    return { word: v.preset ? word : 'Idle', color: modeColor, tint: 0 };
  }

  _render() {
    const st = this._state();
    if (!st) return;
    if (!this._built) this._build();
    const e = this._els;
    const v = this._view(st);
    const s = this._status(v);
    const a = v.a;
    e.card.style.backgroundColor = s.tint ? `color-mix(in srgb, ${s.color} ${s.tint}%, var(--card-background-color))` : 'var(--card-background-color)';
    e.title.textContent = this.config.name || a.friendly_name || this.config.entity;
    e.title.style.color = s.color;
    e.word.textContent = s.word;
    const off = v.mode === 'off' || v.mode === 'unavailable';
    const hasTarget = !off && v.target != null;
    e.target.textContent = off ? 'Off' : hasTarget ? `${Number(v.target).toFixed(1)}°` : cap(v.mode);
    e.targetLabel.textContent = hasTarget ? 'target' : '';
    e.room.textContent = a.current_temperature != null ? `${Number(a.current_temperature).toFixed(1)}°` : '–';
    e.extra.textContent = [a.current_humidity != null ? `${Math.round(a.current_humidity)}% humidity` : '', this._demo ? 'Demo' : ''].filter(Boolean).join(' · ');
    e.down.disabled = !hasTarget;
    e.up.disabled = !hasTarget;
    this._drawGauge(a, v, s, hasTarget);
    this._drawQuick(st, v);
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

  _quickList(st) {
    if (this.config.hide_quick_settings) return [];
    const list = Array.isArray(this.config.quick_settings) ? this.config.quick_settings : ccDefaultQuick(st);
    return list.filter((q) => q && q.name).slice(0, CC_MAX_QUICK);
  }

  _matches(q, v) {
    if (q.hvac_mode && q.hvac_mode !== v.mode) return false;
    if (!q.hvac_mode && v.mode === 'off') return false;
    if (q.preset_mode && !noPreset(q.preset_mode) ? q.preset_mode !== v.preset : v.preset && (q.hvac_mode || q.temperature != null)) return false;
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
      const look = (q.preset_mode && CC_PRESETS[q.preset_mode]) || (q.hvac_mode && CC_MODES[q.hvac_mode]) || CC_MODES.heat;
      const colors = q.color ? [stcColor(q.color), `color-mix(in srgb, ${stcColor(q.color)} 70%, #000)`] : look.colors;
      const icon = q.icon || look.icon;
      const tile = document.createElement('button');
      tile.className = `cc-q${active !== -1 && i !== active ? ' cc-dim' : ''}`;
      tile.title = q.name;
      tile.setAttribute('aria-label', q.name);
      tile.style.background = `linear-gradient(135deg, ${colors[0]}, ${colors[1]})`;
      if (i === active) tile.style.boxShadow = `0 0 12px 2px color-mix(in srgb, ${colors[0]} 80%, transparent)`;
      tile.innerHTML = `<div style="position:absolute; inset:0; background:rgba(0,0,0,0.18);"></div>
        <div style="position:relative; display:flex; align-items:center; justify-content:center; gap:6px; height:100%; padding:0 8px;">
          ${iconHtml(icon, { size: '22px', style: 'flex-shrink:0; filter:drop-shadow(0 1px 3px rgba(0,0,0,0.55));' })}
          <span class="cc-q-name" style="min-width:0; font-weight:600; text-shadow:0 1px 2px rgba(0,0,0,0.6); white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></span>
        </div>`;
      tile.querySelector('.cc-q-name').textContent = q.name;
      tile.addEventListener('click', () => this._applyQuick(q, st));
      box.appendChild(tile);
    });
    hydrateIcons(box);
  }

  _call(service, data) {
    if (this._demo) return;
    this._hass.callService('climate', service, { entity_id: this.config.entity, ...data });
  }

  _applyQuick(q) {
    const st = this._state();
    const a = st.attributes;
    if (this._demo) {
      if (q.hvac_mode) this._demo.state = q.hvac_mode;
      if (q.preset_mode) this._demo.attributes.preset_mode = q.preset_mode;
      else if (q.hvac_mode || q.temperature != null) this._demo.attributes.preset_mode = 'none';
      if (q.temperature != null) this._demo.attributes.temperature = Number(q.temperature);
      if (this._demo.state === 'off') this._demo.attributes.hvac_action = 'off';
      else {
        if (this._demo.attributes.temperature == null) this._demo.attributes.temperature = 20;
        const t = this._demo.attributes.preset_mode === 'eco' ? 16.5 : this._demo.attributes.temperature;
        this._demo.attributes.temperature = t;
        this._demo.attributes.hvac_action = this._demo.attributes.current_temperature < t ? 'heating' : 'idle';
      }
      this._render();
      return;
    }
    if (q.temperature != null) {
      this._call('set_temperature', { temperature: Number(q.temperature), ...(q.hvac_mode ? { hvac_mode: q.hvac_mode } : {}) });
    } else if (q.hvac_mode && q.hvac_mode !== st.state) {
      this._call('set_hvac_mode', { hvac_mode: q.hvac_mode });
    }
    if (q.preset_mode) {
      this._call('set_preset_mode', { preset_mode: q.preset_mode });
    } else if ((q.hvac_mode || q.temperature != null) && !noPreset(a.preset_mode) && (a.preset_modes || []).includes('none') && q.hvac_mode !== 'off') {
      // A plain mode or temperature setting leaves any preset (e.g. Eco).
      this._call('set_preset_mode', { preset_mode: 'none' });
    }
  }

  // − / + change the target locally and send it once tapping stops.
  _step(dir) {
    const st = this._state();
    if (!st) return;
    const a = st.attributes;
    const current = this._pending != null ? this._pending : a.temperature;
    if (current == null) return;
    const step = Number(a.target_temp_step) || (this.config.demo ? 0.5 : 0.5);
    const min = a.min_temp != null ? a.min_temp : 7, max = a.max_temp != null ? a.max_temp : 35;
    const next = Math.round(Math.min(max, Math.max(min, current + dir * step)) * 10) / 10;
    if (this._demo) {
      this._demo.attributes.temperature = next;
      this._demo.attributes.hvac_action = this._demo.state === 'off' ? 'off' : this._demo.attributes.current_temperature < next ? 'heating' : 'idle';
      this._render();
      return;
    }
    this._pending = next;
    this._render();
    clearTimeout(this._sendTimer);
    this._sendTimer = setTimeout(() => {
      this._call('set_temperature', { temperature: next });
      // Keep showing the new target until HA reports it.
      setTimeout(() => {
        this._pending = null;
        this._render();
      }, 3000);
    }, 700);
  }

  getCardSize() {
    return 5;
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
    description: 'A thermostat, radiator valve or air conditioner: gauge, − / + and quick settings',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
