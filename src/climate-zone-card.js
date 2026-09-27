// Climate Zone Card: one floor or zone, with a block per room or sensor.
// Each block shows the room's temperature (in a comfort colour: cold blue,
// cool cyan, comfortable green, warm orange, hot red) and humidity (purple),
// with its own 24-hour graph: temperature filled, humidity as a purple line
// on its own scale. The title takes the zone's average comfort colour.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { KIT_COLOR, kitShell, kitHead, kitGraph, kitRange, kitNum, kitMoreInfo, kitDemoSeries, KitHistory } from './card-kit.js';

// Comfort bands (°C): below `cold` is cold, then cool, comfortable up to
// `warm`, warm, and hot from `hot`.
const CZ_BANDS = { cold: 18, cool: 20, warm: 22.5, hot: 24.5 };

function czComfort(t, c) {
  const b = { cold: c.cold_below ?? CZ_BANDS.cold, cool: c.cool_below ?? CZ_BANDS.cool, warm: c.warm_from ?? CZ_BANDS.warm, hot: c.hot_from ?? CZ_BANDS.hot };
  if (t == null) return { color: KIT_COLOR.off, word: '' };
  if (t < b.cold) return { color: KIT_COLOR.cold, word: 'Cold' };
  if (t < b.cool) return { color: KIT_COLOR.cool, word: 'Cool' };
  if (t < b.warm) return { color: KIT_COLOR.comfy, word: 'Comfortable' };
  if (t < b.hot) return { color: KIT_COLOR.warm, word: 'Warm' };
  return { color: KIT_COLOR.hot, word: 'Hot' };
}

const avg = (list) => (list.length ? list.reduce((a, b) => a + b, 0) / list.length : null);

// Pretend floors for the Design Presets page.
const CZ_DEMO = {
  ground: {
    title: 'Ground Floor',
    rooms: [
      { name: 'Living Room', icon: 'mdi:sofa', t: 21.8, h: 61, ts: [19.1, 18.9, 18.8, 18.9, 19.6, 20.4, 20.9, 20.6, 21.0, 21.4, 21.6, 21.8], hs: [59, 60, 60, 61, 60, 58, 56, 57, 58, 60, 62, 61] },
      { name: 'Entrance', icon: 'mdi:coat-rack', t: 20.9, ts: [18.4, 18.2, 18.1, 18.3, 18.9, 19.6, 20.1, 20.3, 20.5, 20.7, 20.8, 20.9] },
    ],
  },
  top: {
    title: "Hayley's Floor",
    rooms: [
      { name: "Hayley's Bedroom", icon: 'mdi:bed-king', t: 23.0, h: 48.5, ts: [20.9, 20.7, 20.6, 20.9, 21.4, 22.0, 22.4, 22.6, 22.8, 22.9, 23.0, 23.0], hs: [51, 52, 52, 51, 50, 48, 47, 47.5, 48, 49, 48.5, 48.5] },
      { name: "Hayley's Office", icon: 'mdi:chair-rolling', t: 22.2, h: 58, ts: [19.8, 19.6, 19.5, 19.7, 20.2, 20.8, 21.3, 21.5, 21.8, 22.0, 22.1, 22.2], hs: [59, 60, 60, 59, 58, 56, 55, 56, 57, 58, 58, 58] },
      { name: "Hayley's Landing", icon: 'mdi:stairs', t: 17.6, ts: [16.4, 16.2, 16.1, 16.3, 16.8, 17.2, 17.5, 17.6, 17.4, 17.5, 17.6, 17.6] },
    ],
  },
};

export const ClimateZoneCardEditor = createFormEditor({
  schema: (config) => [
    { name: 'title', selector: { text: {} } },
    ...(config.demo
      ? []
      : [
          {
            name: 'rooms',
            selector: {
              object: {
                multiple: true,
                label_field: 'name',
                fields: {
                  name: { label: 'Name', required: true, selector: { text: {} } },
                  temperature: { label: 'Temperature sensor', selector: { entity: { domain: 'sensor', device_class: 'temperature' } } },
                  humidity: { label: 'Humidity sensor (optional)', selector: { entity: { domain: 'sensor', device_class: 'humidity' } } },
                  icon: { label: 'Icon (optional)', selector: { icon: {} } },
                  note: { label: 'Small text under the name (optional)', selector: { text: {} } },
                },
              },
            },
          },
        ]),
    {
      type: 'expandable',
      name: '',
      title: 'Rows and graphs',
      flatten: true,
      schema: [
        { name: 'show_graphs', selector: { boolean: {} }, default: true },
        { name: 'show_humidity_graph', selector: { boolean: {} }, default: true },
        { name: 'hours', selector: { number: { min: 1, max: 168, mode: 'box', unit_of_measurement: 'hours' } } },
      ],
    },
    {
      type: 'expandable',
      name: '',
      title: 'Comfort colours (°C)',
      flatten: true,
      schema: [
        { name: 'cold_below', selector: { number: { min: 5, max: 30, step: 0.5, mode: 'box' } } },
        { name: 'cool_below', selector: { number: { min: 5, max: 30, step: 0.5, mode: 'box' } } },
        { name: 'warm_from', selector: { number: { min: 5, max: 35, step: 0.5, mode: 'box' } } },
        { name: 'hot_from', selector: { number: { min: 5, max: 40, step: 0.5, mode: 'box' } } },
      ],
    },
    {
      type: 'expandable',
      name: '',
      title: 'Demo mode (pretend rooms, for Design Presets)',
      flatten: true,
      schema: [
        { name: 'demo', selector: { boolean: {} } },
        { name: 'demo_floor', selector: { select: { mode: 'dropdown', options: [{ value: 'ground', label: 'Ground floor' }, { value: 'top', label: 'Top floor (one cold landing)' }] } } },
      ],
    },
  ],
  labels: {
    title: 'Title (e.g. the floor)',
    rooms: 'Rooms and sensors',
    show_graphs: 'A graph for each room',
    show_humidity_graph: 'Humidity line on the graphs',
    hours: 'Graph length',
    cold_below: 'Cold below (blue)',
    cool_below: 'Cool below (cyan)',
    warm_from: 'Warm from (orange)',
    hot_from: 'Hot from (red)',
    demo: 'Use pretend rooms instead of real sensors',
    demo_floor: 'Pretend floor',
  },
  helpers: {
    hours: 'Default 24.',
    cold_below: 'Defaults: cold below 18, cool below 20, warm from 22.5, hot from 24.5. Between cool and warm is comfortable (green).',
  },
});

export class ClimateZoneCard extends HTMLElement {
  setConfig(config) {
    if (!config.demo && !Array.isArray(config.rooms)) throw new Error('rooms required (or set demo: true)');
    this.config = config;
    this._built = false;
    const hours = Number(config.hours) || 24;
    const ids = (config.rooms || []).flatMap((r) => [r.temperature, r.humidity]).filter(Boolean);
    this._hist = config.demo ? null : new KitHistory(this, ids, hours);
    this._sig = null;
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
  }

  _rooms() {
    const c = this.config;
    if (c.demo) {
      const d = CZ_DEMO[c.demo_floor] || CZ_DEMO.ground;
      return d.rooms.map((r) => ({ ...r, tPts: kitDemoSeries(r.ts), hPts: r.hs ? kitDemoSeries(r.hs) : null, note: 'Demo sensor' }));
    }
    const s = (id) => id && this._hass && this._hass.states[id];
    const data = (this._hist && this._hist.data) || {};
    return (c.rooms || []).map((r) => {
      const ts = s(r.temperature), hs = s(r.humidity);
      return {
        name: r.name || (ts && ts.attributes.friendly_name) || r.temperature,
        icon: r.icon || (ts && ts.attributes.icon) || 'mdi:thermometer',
        note: r.note || '',
        t: kitNum(ts),
        h: kitNum(hs),
        tPts: data[r.temperature] || null,
        hPts: r.humidity ? data[r.humidity] || null : null,
        entity: r.temperature,
        hasHumidity: !!r.humidity,
      };
    });
  }

  _render() {
    if (!this._hass || !this.config) return;
    const c = this.config;
    if (!this._built) {
      this.innerHTML = kitShell(`<div class="cz-rooms" style="display:flex; flex-direction:column; gap:6px;"></div>`);
      this._box = this.querySelector('.cz-rooms');
      this._built = true;
    }
    if (this._hist && this._hist.due()) this._hist.load(this._hass);
    const rooms = this._rooms();
    const showGraphs = c.show_graphs !== false;
    const showHum = c.show_humidity_graph !== false;
    const temps = rooms.map((r) => r.t).filter((v) => v != null);
    const hums = rooms.map((r) => r.h).filter((v) => v != null);
    const t = avg(temps), h = avg(hums);
    const zone = czComfort(t, c);
    const title = c.title || (c.demo ? (CZ_DEMO[c.demo_floor] || CZ_DEMO.ground).title : 'Climate');
    kitHead(this, title, [t != null ? `${t.toFixed(1)}°` : '', h != null ? `${Math.round(h)}%` : ''].filter(Boolean).join(' · ') + (c.demo ? ' · demo' : ''), zone.color);
    const sig = JSON.stringify([rooms.map((r) => [r.name, r.icon, r.note, r.t, r.h]), showGraphs, showHum, this._hist && this._hist.at, c]);
    if (sig === this._sig) return;
    this._sig = sig;
    const hours = Number(c.hours) || 24;
    this._box.innerHTML = rooms
      .map((r, i) => {
        const cz = czComfort(r.t, c);
        const graph = showGraphs
          ? kitGraph(
              [
                { pts: r.tPts, current: r.t, color: cz.color, fill: true },
                ...(showHum && r.hPts ? [{ pts: r.hPts, current: r.h, color: KIT_COLOR.humidity, pad: 3, width: 1.8 }] : []),
              ],
              { hours, height: 44, label: `${r.name}: last ${hours} hours` }
            )
          : '';
        const tRange = showGraphs ? kitRange(r.tPts, r.t, 1, '°') : '';
        const hRange = showGraphs && showHum && r.hPts ? kitRange(r.hPts, r.h, 0, '%') : '';
        return `<div class="ck-tap cz-room" data-i="${i}" tabindex="0" role="button" style="display:flex; flex-direction:column; gap:4px; padding:8px; border-radius:12px; background:rgba(127,127,127,0.07);">
          <div style="display:flex; align-items:center; gap:10px;">
            ${iconHtml(r.icon, { size: '22px', style: `color:${cz.color}; flex:none;` })}
            <div style="flex:1; min-width:0;">
              <div class="cz-name" style="font-size:0.95rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></div>
              <div class="cz-note ck-sub" style="font-size:0.72rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></div>
            </div>
            ${r.h != null ? `<span style="font-size:0.85rem; color:${KIT_COLOR.humidity}; font-variant-numeric:tabular-nums;">${Math.round(r.h * 10) / 10}%</span>` : ''}
            <b style="min-width:54px; text-align:right; font-size:1.15rem; font-variant-numeric:tabular-nums; color:${cz.color};">${r.t != null ? `${r.t.toFixed(1)}°` : '–'}</b>
          </div>
          ${graph}
          ${graph ? `<div style="display:flex; justify-content:space-between; gap:8px; font-size:0.72rem; color:var(--secondary-text-color);"><span style="color:${cz.color};">${tRange}</span><span style="color:${hRange ? KIT_COLOR.humidity : 'inherit'};">${hRange || (r.hasHumidity || r.hPts ? '' : 'temperature only')}</span></div>` : ''}
          ${showGraphs && !graph && !c.demo ? `<div class="ck-sub" style="font-size:0.72rem;">${this._hist && this._hist.data ? 'No history yet' : 'Loading history…'}</div>` : ''}
        </div>`;
      })
      .join('');
    this._box.querySelectorAll('.cz-room').forEach((el) => {
      const r = rooms[Number(el.dataset.i)];
      el.querySelector('.cz-name').textContent = r.name;
      el.querySelector('.cz-note').textContent = r.note || czComfort(r.t, c).word;
      const open = () => !c.demo && kitMoreInfo(this, r.entity);
      el.addEventListener('click', open);
      el.addEventListener('keydown', (ev) => (ev.key === 'Enter' || ev.key === ' ') && open());
    });
    hydrateIcons(this);
  }

  getCardSize() {
    const n = this.config.demo ? 2 : (this.config.rooms || []).length;
    return 1 + n * (this.config.show_graphs === false ? 1 : 2);
  }

  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`climate-zone-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { demo: true, title: 'Ground Floor' };
  }
}

export function registerClimateZoneCard() {
  if (!customElements.get(`climate-zone-card-editor${SUFFIX}`)) customElements.define(`climate-zone-card-editor${SUFFIX}`, ClimateZoneCardEditor);
  if (!customElements.get(`climate-zone-card${SUFFIX}`)) customElements.define(`climate-zone-card${SUFFIX}`, ClimateZoneCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `climate-zone-card${SUFFIX}`,
    name: `Climate Zone Card${LABEL}`,
    description: 'A floor or zone: temperature and humidity per room, each with its own 24-hour graph',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
