// Fan Card: a fan with speeds and presets (e.g. the Philips bedroom fan).
// Title in cyan while on (grey when off) with a status word, a gauge of the
// speed, then two rows of tiles like the alarm card's buttons: Off and the
// speeds (1, 2, 3...), then the other presets (Natural, Sleep...) and
// Oscillate. Grey until selected; the selected one fills with colour.
//
// Speeds come from presets named like "speed_1" (Philips) or, failing that,
// from the fan's percentage steps.

import { createFormEditor } from './form-editor.js';
import { hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { KIT_COLOR, kitShell, kitHead, kitGauge, kitTiles, kitCap, kitNum, kitMoreInfo, KitPending, kitHealthBanner } from './card-kit.js';

const FAN_PRESET_ICONS = {
  natural: 'mdi:weather-windy',
  nature: 'mdi:weather-windy',
  breeze: 'mdi:weather-windy',
  sleep: 'mdi:power-sleep',
  auto: 'mdi:fan-auto',
  smart: 'mdi:fan-auto',
  turbo: 'mdi:rocket-launch',
  boost: 'mdi:rocket-launch',
  eco: 'mdi:leaf',
};
const speedOf = (preset) => {
  const m = /^speed[ _-]?(\d+)$/i.exec(String(preset || ''));
  return m ? Number(m[1]) : null;
};

function fanDemo(config) {
  const on = config.demo_state === 'on';
  return {
    entity_id: 'fan.demo',
    state: on ? 'on' : 'off',
    attributes: {
      friendly_name: 'Bedroom Fan',
      preset_modes: ['speed_1', 'speed_2', 'speed_3', 'natural', 'sleep'],
      preset_mode: on ? 'speed_2' : null,
      percentage: on ? 67 : null,
      percentage_step: 33.333333333333336,
      oscillating: on,
      supported_features: 59,
      model_id: 'CX3550/01',
    },
  };
}

// The speeds a fan offers: [{ n, preset?, percentage? }].
function fanSpeeds(a) {
  const presets = (a.preset_modes || []).filter((p) => speedOf(p) != null).sort((x, y) => speedOf(x) - speedOf(y));
  if (presets.length) return presets.map((p) => ({ n: speedOf(p), preset: p }));
  const step = Number(a.percentage_step) || 0;
  const count = step ? Math.round(100 / step) : 0;
  if (count < 2 || count > 6) return [];
  return Array.from({ length: count }, (_, i) => ({ n: i + 1, percentage: Math.round(step * (i + 1)) }));
}

export const FanCardEditor = createFormEditor({
  schema: (config) => [
    ...(config.demo ? [] : [{ name: 'entity', selector: { entity: { domain: 'fan' } } }]),
    { name: 'name', selector: { text: {} } },
    { name: 'temperature_entity', selector: { entity: { domain: 'sensor', device_class: 'temperature' } } },
    {
      type: 'expandable',
      name: '',
      title: 'Rows to show',
      flatten: true,
      schema: [
        { name: 'show_gauge', selector: { boolean: {} }, default: true },
        { name: 'show_speeds', selector: { boolean: {} }, default: true },
        { name: 'show_presets', selector: { boolean: {} }, default: true },
        { name: 'show_oscillate', selector: { boolean: {} }, default: true },
      ],
    },
    {
      type: 'expandable',
      name: '',
      title: 'Demo mode (a pretend fan, for Design Presets)',
      flatten: true,
      schema: [
        { name: 'demo', selector: { boolean: {} } },
        { name: 'demo_state', selector: { select: { mode: 'dropdown', options: [{ value: 'off', label: 'Off' }, { value: 'on', label: 'On (speed 2)' }] } } },
      ],
    },
  ],
  labels: {
    entity: 'Fan',
    name: 'Title (optional)',
    temperature_entity: 'Room temperature (optional, shown under the gauge)',
    show_gauge: 'Speed gauge',
    show_speeds: 'Off and speed buttons',
    show_presets: 'Preset buttons (Natural, Sleep…)',
    show_oscillate: 'Oscillate button (if the fan can)',
    demo: 'Use a pretend fan instead of a real one',
    demo_state: 'Pretend fan starts',
  },
});

export class FanCard extends HTMLElement {
  setConfig(config) {
    if (!config.entity && !config.demo) throw new Error('entity required (or set demo: true)');
    this.config = config;
    this._built = false;
    this._demo = config.demo ? fanDemo(config) : null;
    this._pending = new KitPending(this);
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
  }

  _state() {
    return this._pending.apply(this._demo || (this._hass && this._hass.states[this.config.entity]));
  }

  _render() {
    const st = this._state();
    if (!st || !this._hass) return;
    const c = this.config;
    if (!this._built) {
      this.innerHTML = kitShell(`
        <div class="fc-top" style="display:flex; align-items:center; gap:14px;">
          <div class="fc-gauge ck-tap"></div>
          <div class="ck-info fc-info"></div>
        </div>
        <div class="ck-row fc-speeds"></div>
        <div class="ck-row fc-presets"></div>`);
      this.querySelector('.fc-gauge').addEventListener('click', () => !this._demo && kitMoreInfo(this, c.entity));
      this._built = true;
    }
    const a = st.attributes;
    const on = st.state === 'on';
    const speeds = fanSpeeds(a);
    const current = on ? speeds.find((s) => (s.preset ? s.preset === a.preset_mode : Math.abs((a.percentage || 0) - s.percentage) < 5)) : null;
    const preset = on && a.preset_mode && speedOf(a.preset_mode) == null ? a.preset_mode : null;
    const color = !on ? KIT_COLOR.off : preset === 'sleep' ? KIT_COLOR.sleep : KIT_COLOR.fan;
    const word = st.state === 'unavailable' ? 'Unavailable' : !on ? 'Off' : preset ? kitCap(preset) : current ? `Speed ${current.n}` : 'On';
    kitHead(this, c.name || a.friendly_name || c.entity, word + (this._demo ? ' · demo' : ''), color, on ? 10 : 0);
    kitHealthBanner(this, this._hass, c.entity, !!(this._demo || c.demo));

    // Gauge and info.
    const top = this.querySelector('.fc-top');
    top.style.display = c.show_gauge === false ? 'none' : 'flex';
    const level = !on ? 0 : current ? current.n / (speeds.length || 1) : (a.percentage || 100) / 100;
    this.querySelector('.fc-gauge').innerHTML = kitGauge(level, color, !on ? 'Off' : current ? String(current.n) : preset ? kitCap(preset) : 'On', !on ? '' : current ? 'speed' : '', 72);
    const temp = c.temperature_entity && this._hass.states[c.temperature_entity];
    const tv = kitNum(temp);
    const lines = [];
    if (a.model_id) lines.push(`<span>${a.model_id}</span>`);
    if (a.oscillating != null) lines.push(`<span>Oscillate ${a.oscillating ? 'on' : 'off'}</span>`);
    if (tv != null) lines.push(`<span>${tv.toFixed(1)}° in the room</span>`);
    this.querySelector('.fc-info').innerHTML = lines.join('');

    // Row 1: Off and speeds.
    const speedTiles = c.show_speeds === false ? [] : [
      { key: 'off', name: 'Off', icon: 'mdi:power', color: KIT_COLOR.off, on: !on },
      ...speeds.map((s, i) => ({
        key: `s${s.n}`,
        name: String(s.n),
        icon: ['mdi:speedometer-slow', 'mdi:speedometer-medium', 'mdi:speedometer'][Math.min(2, Math.round((i / Math.max(1, speeds.length - 1)) * 2))],
        color: KIT_COLOR.fan,
        on: current === s,
        speed: s,
      })),
    ];
    kitTiles(this.querySelector('.fc-speeds'), speedTiles, (t) => (t.key === 'off' ? this._call('turn_off', {}) : this._setSpeed(t.speed)));

    // Row 2: other presets and oscillate.
    const others = (a.preset_modes || []).filter((p) => speedOf(p) == null);
    const canOscillate = a.oscillating != null || ((a.supported_features || 0) & 2) === 2;
    const presetTiles = [
      ...(c.show_presets === false ? [] : others.map((p) => ({ key: p, name: kitCap(p), icon: FAN_PRESET_ICONS[String(p).toLowerCase()] || 'mdi:fan', color: p === 'sleep' ? KIT_COLOR.sleep : KIT_COLOR.fan, on: preset === p }))),
      ...(c.show_oscillate === false || !canOscillate ? [] : [{ key: '__osc', name: 'Oscillate', icon: 'mdi:arrow-oscillating', color: KIT_COLOR.fan, on: on && !!a.oscillating }]),
    ];
    kitTiles(this.querySelector('.fc-presets'), presetTiles, (t) => {
      if (t.key === '__osc') this._call('oscillate', { oscillating: !this._state().attributes.oscillating });
      else this._call('set_preset_mode', { preset_mode: t.key });
    });
    hydrateIcons(this);
  }

  _setSpeed(s) {
    if (s.preset) this._call('set_preset_mode', { preset_mode: s.preset });
    else this._call('set_percentage', { percentage: s.percentage });
  }

  _call(service, data) {
    const st = this._state();
    if (!this._demo && st) {
      if (service === 'turn_off') this._pending.set({ state: 'off' });
      else if (service === 'oscillate') this._pending.set({ state: st.state, attrs: { oscillating: data.oscillating } });
      else if (service === 'set_percentage') this._pending.set({ state: 'on', attrs: { percentage: data.percentage } });
      else this._pending.set({ state: 'on', attrs: { preset_mode: data.preset_mode } });
      this._render();
    }
    if (this._demo) {
      const d = this._demo, a = d.attributes;
      if (service === 'turn_off') {
        d.state = 'off';
        a.preset_mode = null;
      } else if (service === 'oscillate') {
        a.oscillating = data.oscillating;
      } else {
        d.state = 'on';
        if (data.preset_mode) a.preset_mode = data.preset_mode;
      }
      this._render();
      return;
    }
    this._hass.callService('fan', service, { entity_id: this.config.entity, ...data });
  }

  getCardSize() {
    return 4;
  }

  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`fan-card-editor${SUFFIX}`);
  }

  static getStubConfig(hass) {
    const first = hass && Object.keys(hass.states).find((id) => id.startsWith('fan.'));
    return first ? { entity: first } : { demo: true };
  }
}

export function registerFanCard() {
  if (!customElements.get(`fan-card-editor${SUFFIX}`)) customElements.define(`fan-card-editor${SUFFIX}`, FanCardEditor);
  if (!customElements.get(`fan-card${SUFFIX}`)) customElements.define(`fan-card${SUFFIX}`, FanCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `fan-card${SUFFIX}`,
    name: `Fan Card${LABEL}`,
    description: 'A fan: speed gauge, Off and speed buttons, presets and oscillate',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
