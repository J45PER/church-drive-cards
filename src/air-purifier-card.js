// Air Purifier Card: a purifier fan with its air-quality sensors.
// Title in the air-quality colour (grey when off) with the mode and quality;
// a PM2.5 gauge (good green, fair amber, poor orange, very poor red), the
// allergen index, a 24-hour PM2.5 graph, mode tiles (Off, Auto, Medium,
// Turbo, Sleep… grey until selected) and filter life bars. Every extra can
// be switched off in the editor. Picking the purifier finds its PM2.5,
// allergen and filter sensors automatically (same entity name prefix).

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { KIT_COLOR, kitShell, kitHead, kitGauge, kitTiles, kitGraph, kitRange, kitCap, kitNum, kitMoreInfo, kitDemoSeries, KitHistory, KitPending } from './card-kit.js';

// PM2.5 (µg/m³) bands. Philips purifiers follow the Chinese air-quality
// standard (good up to 35, then 75, 115); each band can be changed per card.
const AP_BANDS = { good_max: 35, fair_max: 75, poor_max: 115 };
const apBand = (c, k) => (c && c[k] != null && c[k] !== '' ? Number(c[k]) : AP_BANDS[k]);
function apQuality(pm, c) {
  if (pm == null) return { color: KIT_COLOR.off, word: '' };
  if (pm <= apBand(c, 'good_max')) return { color: KIT_COLOR.good, word: 'Good' };
  if (pm <= apBand(c, 'fair_max')) return { color: KIT_COLOR.fair, word: 'Fair' };
  if (pm <= apBand(c, 'poor_max')) return { color: KIT_COLOR.poor, word: 'Poor' };
  return { color: KIT_COLOR.bad, word: 'Very poor' };
}
// Philips indoor allergen index, 1 to 12.
function apAllergen(v) {
  if (v == null) return null;
  if (v <= 3) return { color: KIT_COLOR.good, word: 'Low' };
  if (v <= 6) return { color: KIT_COLOR.fair, word: 'Moderate' };
  if (v <= 9) return { color: KIT_COLOR.poor, word: 'High' };
  return { color: KIT_COLOR.bad, word: 'Very high' };
}
const AP_MODE_ICONS = {
  auto: 'mdi:autorenew',
  'auto (general)': 'mdi:autorenew',
  allergen: 'mdi:flower',
  medium: 'mdi:fan',
  turbo: 'mdi:rocket-launch',
  sleep: 'mdi:power-sleep',
  night: 'mdi:power-sleep',
  low: 'mdi:fan-speed-1',
  high: 'mdi:fan-speed-3',
};

const AP_ROWS = { show_gauge: true, show_allergen: true, show_graph: true, show_modes: true, show_filters: true };
const row = (c, k) => (c[k] != null ? !!c[k] : AP_ROWS[k]);

function apDemo() {
  return {
    fan: {
      entity_id: 'fan.demo_purifier',
      state: 'on',
      attributes: { friendly_name: 'Air Purifier', preset_modes: ['auto', 'turbo', 'medium', 'sleep'], preset_mode: 'auto', model_id: 'AC0951/13' },
    },
    pm25: 30,
    allergen: 3,
    filters: [
      { name: 'NanoProtect filter', value: 90, hours: 8594 },
      { name: 'Pre-filter', value: 18, hours: 130 },
    ],
    pmPts: kitDemoSeries([8, 7, 9, 12, 18, 24, 31, 28, 22, 26, 33, 30]),
  };
}

// Sensors next to the purifier: same object id prefix.
function apSiblings(hass, fanId) {
  const base = String(fanId || '').split('.')[1];
  if (!hass || !base) return {};
  const ids = Object.keys(hass.states).filter((id) => id.startsWith(`sensor.${base}_`));
  const find = (re) => ids.find((id) => re.test(id));
  return {
    pm25_entity: find(/pm2_?5/),
    allergen_entity: find(/allergen/),
    filters: ids.filter((id) => /filter/.test(id) && !isNaN(Number(hass.states[id].state))).map((entity) => ({ entity })),
  };
}

const filterName = (hass, f) => {
  if (f.name) return f.name;
  const st = hass && hass.states[f.entity];
  const name = (st && st.attributes.friendly_name) || f.entity;
  return name.replace(/^.*?(pre-?filter|nanoprotect filter|hepa filter|carbon filter|filter)/i, '$1').replace(/^./, (c) => c.toUpperCase());
};

export const AirPurifierCardEditor = createFormEditor({
  fill: (config, hass) => {
    if (config.demo || !config.entity || config.pm25_entity !== undefined || !hass) return config;
    const found = apSiblings(hass, config.entity);
    return { ...config, pm25_entity: found.pm25_entity || '', allergen_entity: found.allergen_entity || '', filters: found.filters || [] };
  },
  schema: (config) => [
    ...(config.demo ? [] : [{ name: 'entity', selector: { entity: { domain: 'fan' } } }]),
    { name: 'name', selector: { text: {} } },
    {
      type: 'expandable',
      name: '',
      title: 'Rows to show',
      flatten: true,
      schema: Object.keys(AP_ROWS).map((name) => ({ name, selector: { boolean: {} }, default: AP_ROWS[name] })),
    },
    ...(config.demo
      ? []
      : [
          {
            type: 'expandable',
            name: '',
            title: 'Sensors (found automatically)',
            flatten: true,
            schema: [
              { name: 'pm25_entity', selector: { entity: { domain: 'sensor' } } },
              { name: 'allergen_entity', selector: { entity: { domain: 'sensor' } } },
              {
                name: 'filters',
                selector: {
                  object: {
                    multiple: true,
                    label_field: 'name',
                    fields: {
                      entity: { label: 'Filter life sensor (%)', required: true, selector: { entity: { domain: 'sensor' } } },
                      name: { label: 'Name (optional)', selector: { text: {} } },
                    },
                  },
                },
              },
            ],
          },
        ]),
    {
      type: 'expandable',
      name: '',
      title: 'Air quality bands (PM2.5 µg/m³)',
      flatten: true,
      schema: Object.keys(AP_BANDS).map((name) => ({ name, selector: { number: { min: 1, max: 500, mode: 'box', unit_of_measurement: 'µg/m³' } } })),
    },
    {
      type: 'expandable',
      name: '',
      title: 'Demo mode (a pretend purifier, for Design Presets)',
      flatten: true,
      schema: [{ name: 'demo', selector: { boolean: {} } }],
    },
  ],
  labels: {
    entity: 'Air purifier (fan)',
    name: 'Title (optional)',
    show_gauge: 'PM2.5 gauge and air quality',
    show_allergen: 'Allergen index',
    show_graph: 'PM2.5 graph (24 hours)',
    show_modes: 'Mode buttons',
    show_filters: 'Filter life',
    pm25_entity: 'PM2.5 sensor',
    allergen_entity: 'Allergen index sensor (optional)',
    filters: 'Filters',
    good_max: 'Good up to',
    fair_max: 'Fair up to',
    poor_max: 'Poor up to (above is very poor)',
    demo: 'Use a pretend purifier instead of a real one',
  },
  helpers: {
    good_max: 'Defaults 35 / 75 / 115, the Chinese standard Philips purifiers use, so the card matches the Philips app.',
    filters: 'Shown as bars; amber under 25% ("Clean soon" for a pre-filter, "Replace soon" otherwise), red under 10%.',
  },
});

export class AirPurifierCard extends HTMLElement {
  setConfig(config) {
    if (!config.entity && !config.demo) throw new Error('entity required (or set demo: true)');
    this.config = config;
    this._built = false;
    this._demo = config.demo ? apDemo() : null;
    this._pending = new KitPending(this);
    this._hist = config.demo || !config.pm25_entity ? null : new KitHistory(this, [config.pm25_entity], 24);
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
  }

  _render() {
    if (!this._hass) return;
    const c = this.config;
    const st = this._pending.apply(this._demo ? this._demo.fan : this._hass.states[c.entity]);
    if (!st) return;
    if (!this._built) {
      this.innerHTML = kitShell(`
        <div class="ap-top" style="display:flex; align-items:center; gap:14px;">
          <div class="ap-gauge ck-tap"></div>
          <div class="ck-info ap-info"></div>
          <div class="ap-quality" style="flex:none; text-align:right;"></div>
        </div>
        <div class="ap-graph"></div>
        <div class="ck-row ap-modes"></div>
        <div class="ap-filters" style="display:flex; flex-direction:column; gap:10px;"></div>`);
      this.querySelector('.ap-gauge').addEventListener('click', () => !this._demo && kitMoreInfo(this, c.pm25_entity || c.entity));
      this._built = true;
    }
    if (this._hist && row(c, 'show_graph') && this._hist.due()) this._hist.load(this._hass);
    const a = st.attributes;
    const on = st.state === 'on';
    const pm = this._demo ? this._demo.pm25 : kitNum(c.pm25_entity && this._hass.states[c.pm25_entity]);
    const allergen = this._demo ? this._demo.allergen : kitNum(c.allergen_entity && this._hass.states[c.allergen_entity]);
    const q = apQuality(pm, c);
    const mode = on ? (a.preset_mode ? kitCap(a.preset_mode) : 'On') : st.state === 'unavailable' ? 'Unavailable' : 'Off';
    const color = on ? q.color : KIT_COLOR.off;
    kitHead(this, c.name || a.friendly_name || c.entity, [mode, q.word ? `${q.word} air` : ''].filter(Boolean).join(' · ') + (this._demo ? ' · demo' : ''), color, on && pm > 35 ? 12 : 0);

    // Gauge, info and quality word.
    const top = this.querySelector('.ap-top');
    top.style.display = row(c, 'show_gauge') ? 'flex' : 'none';
    this.querySelector('.ap-gauge').innerHTML = kitGauge(pm == null ? 0 : pm / (apBand(c, 'poor_max') * 1.3), q.color, pm == null ? '–' : String(Math.round(pm)), 'PM2.5 µg/m³');
    const al = row(c, 'show_allergen') ? apAllergen(allergen) : null;
    this.querySelector('.ap-info').innerHTML = [
      al ? `<span>${iconHtml('mdi:flower', { size: '18px', style: `color:${al.color};` })}Allergens ${allergen} · ${al.word}</span>` : '',
      a.model_id ? `<span>${iconHtml('mdi:air-purifier', { size: '18px' })}${a.model_id}</span>` : '',
    ].join('');
    this.querySelector('.ap-quality').innerHTML = q.word
      ? `<div style="font-size:1.35rem; font-weight:700; color:${q.color};">${q.word}</div><div class="ck-sub">air quality</div>`
      : '';

    // PM2.5 graph.
    const gBox = this.querySelector('.ap-graph');
    if (row(c, 'show_graph') && (this._demo || c.pm25_entity)) {
      const pts = this._demo ? this._demo.pmPts : this._hist && this._hist.data ? this._hist.data[c.pm25_entity] : null;
      const svg = kitGraph([{ pts, current: pm, color: q.color, fill: true, pad: 2 }], { height: 48, label: 'PM2.5, last 24 hours' });
      gBox.style.display = 'block';
      gBox.innerHTML = svg
        ? `${svg}<div style="display:flex; justify-content:space-between; font-size:0.78rem; color:var(--secondary-text-color); margin-top:2px;"><span style="color:${q.color};">● PM2.5 ${kitRange(pts, pm, 0, '')}</span><span>last 24 h</span></div>`
        : `<div class="ck-sub">${this._hist && this._hist.data ? 'No PM2.5 history yet' : 'Loading history…'}</div>`;
    } else gBox.style.display = 'none';

    // Modes.
    const presets = a.preset_modes || [];
    const modes = row(c, 'show_modes')
      ? [
          { key: '__off', name: 'Off', icon: 'mdi:power', color: KIT_COLOR.off, on: !on },
          ...presets.map((p) => ({
            key: p,
            name: kitCap(p),
            icon: AP_MODE_ICONS[String(p).toLowerCase()] || 'mdi:fan',
            color: /sleep|night/i.test(p) ? KIT_COLOR.sleep : KIT_COLOR.good,
            on: on && a.preset_mode === p,
          })),
        ]
      : [];
    kitTiles(this.querySelector('.ap-modes'), modes, (t) => this._mode(t.key), { column: true });

    // Filters.
    const fBox = this.querySelector('.ap-filters');
    const filters = !row(c, 'show_filters')
      ? []
      : this._demo
        ? this._demo.filters
        : (c.filters || []).map((f) => {
            const fs = this._hass.states[f.entity];
            return { name: filterName(this._hass, f), value: kitNum(fs), hours: fs ? Number(fs.attributes.time_remaining) : NaN, entity: f.entity };
          });
    fBox.style.display = filters.length ? 'flex' : 'none';
    fBox.innerHTML = filters
      .map((f, i) => {
        const v = f.value == null ? 0 : f.value;
        const fc = v < 10 ? KIT_COLOR.bad : v < 25 ? KIT_COLOR.fair : KIT_COLOR.good;
        const soon = v < 25 ? (/pre/i.test(f.name) ? 'Clean soon' : 'Replace soon') : '';
        const left = !isNaN(f.hours) && f.hours >= 0 ? (f.hours >= 48 ? `${Math.round(f.hours / 24)} days` : `${Math.round(f.hours)} hours`) : '';
        return `<div class="ck-tap ap-filter" data-i="${i}" style="display:grid; grid-template-columns:minmax(0,1fr) auto; gap:4px 10px; font-size:0.85rem;">
          <span style="display:flex; align-items:center; gap:6px; min-width:0;"><span class="ap-fname" style="white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></span>${soon ? `<span class="ck-chip" style="color:${fc}; background:color-mix(in srgb, ${fc} 18%, transparent);">${soon}</span>` : ''}</span>
          <span class="ck-sub" style="font-variant-numeric:tabular-nums;">${f.value == null ? '–' : `${Math.round(v)}%`}${left ? ` · ${left}` : ''}</span>
          <div class="ck-bar" style="grid-column:1/-1;"><i style="width:${Math.max(0, Math.min(100, v))}%; background:${fc};"></i></div>
        </div>`;
      })
      .join('');
    fBox.querySelectorAll('.ap-filter').forEach((el) => {
      const f = filters[Number(el.dataset.i)];
      el.querySelector('.ap-fname').textContent = f.name;
      el.addEventListener('click', () => !this._demo && kitMoreInfo(this, f.entity));
    });
    hydrateIcons(this);
  }

  _mode(key) {
    if (this._demo) {
      const f = this._demo.fan;
      if (key === '__off') f.state = 'off';
      else {
        f.state = 'on';
        f.attributes.preset_mode = key;
      }
      this._render();
      return;
    }
    this._pending.set(key === '__off' ? { state: 'off' } : { state: 'on', attrs: { preset_mode: key } });
    this._render();
    if (key === '__off') this._hass.callService('fan', 'turn_off', { entity_id: this.config.entity });
    else this._hass.callService('fan', 'set_preset_mode', { entity_id: this.config.entity, preset_mode: key });
  }

  getCardSize() {
    const c = this.config;
    return 2 + (row(c, 'show_gauge') ? 2 : 0) + (row(c, 'show_graph') ? 1 : 0) + (row(c, 'show_modes') ? 1 : 0) + (row(c, 'show_filters') ? 1 : 0);
  }

  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`air-purifier-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { demo: true };
  }
}

export function registerAirPurifierCard() {
  if (!customElements.get(`air-purifier-card-editor${SUFFIX}`)) customElements.define(`air-purifier-card-editor${SUFFIX}`, AirPurifierCardEditor);
  if (!customElements.get(`air-purifier-card${SUFFIX}`)) customElements.define(`air-purifier-card${SUFFIX}`, AirPurifierCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `air-purifier-card${SUFFIX}`,
    name: `Air Purifier Card${LABEL}`,
    description: 'An air purifier: PM2.5 gauge, allergen index, 24-hour graph, modes and filter life',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
