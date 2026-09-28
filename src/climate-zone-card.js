// Climate Zone Card: one floor or zone, with a block per room or sensor.
// Each room has a type (living room, bedroom, landing...) with its own
// comfortable range, and its temperature is coloured on a smooth scale around
// that range: teal-green-lime inside it, cyan to deep blue below, amber to
// deep red above, and ice-white with a warning at 0° and below. Each room's
// 24-hour graph shades the comfortable range, colours the temperature line by
// the same scale, and draws humidity (purple) with dotted 40-60% limits.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { KIT_COLOR, kitShell, kitHead, kitRange, kitNum, kitDemoSeries, KitHistory, kitScrub, kitSmooth, kitPath, kitCompact, kitCompactable } from './card-kit.js';

// Comfortable ranges (°C) by room type, from UK guidance (at least 18° in
// living spaces; bedrooms cooler for sleep).
export const CZ_TYPES = {
  living: { name: 'Living room', low: 19, high: 22 },
  bedroom: { name: 'Bedroom', low: 16, high: 20 },
  office: { name: 'Office / study', low: 19, high: 22 },
  hall: { name: 'Hall / landing', low: 16, high: 21 },
  bathroom: { name: 'Bathroom', low: 20, high: 24 },
  kitchen: { name: 'Kitchen', low: 17, high: 21 },
};
const CZ_HUMIDITY = { humidity_low: 40, humidity_high: 60, humidity_dry: 30 };
const FREEZING = '#e3f2fd';

// A room type from the name or icon when none is set.
function czGuessType(r) {
  const s = `${r.name || ''} ${r.icon || ''}`.toLowerCase();
  if (/bed/.test(s)) return 'bedroom';
  if (/office|study|desk|chair/.test(s)) return 'office';
  if (/landing|hall|entrance|stair|coat|porch|corridor/.test(s)) return 'hall';
  if (/bath|en-?suite|shower|toilet|wc/.test(s)) return 'bathroom';
  if (/kitchen|utility/.test(s)) return 'kitchen';
  return 'living';
}

function czRange(r) {
  const t = CZ_TYPES[r.type] || CZ_TYPES[czGuessType(r)];
  const low = r.low != null && r.low !== '' ? Number(r.low) : t.low;
  const high = r.high != null && r.high !== '' ? Number(r.high) : t.high;
  return { low, high: Math.max(high, low + 0.5), typeName: t.name };
}

// Colour stops around a comfortable range; 0° and below is always freezing.
function czStops(low, high) {
  const mid = (low + high) / 2;
  const list = [
    [0.01, '#1a3f9e'], [low - 6, '#1e5fd6'], [low - 3, '#42a5f5'], [low - 1, '#26c6da'], [low, '#26a69a'],
    [mid, '#66bb6a'], [high, '#c0ca33'], [high + 1, '#ffca28'], [high + 2.5, '#ffa726'], [high + 4, '#ef5350'], [high + 6, '#b71c1c'],
  ];
  return list.filter((s, i) => i === 0 || s[0] > list[i - 1][0]);
}
const hex = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));

export function czColour(t, low, high) {
  if (t == null || isNaN(t)) return KIT_COLOR.off;
  if (t <= 0) return FREEZING;
  const s = czStops(low, high);
  if (t <= s[0][0]) return s[0][1];
  for (let i = 0; i < s.length - 1; i++) {
    const [a, ca] = s[i], [b, cb] = s[i + 1];
    if (t <= b) {
      const f = (t - a) / (b - a), A = hex(ca), B = hex(cb);
      return `rgb(${A.map((v, k) => Math.round(v + (B[k] - v) * f)).join(',')})`;
    }
  }
  return s[s.length - 1][1];
}

function czWord(t, low, high) {
  if (t == null) return '';
  if (t <= 0) return 'Freezing';
  if (t < low - 3) return 'Cold';
  if (t < low) return 'Cool';
  if (t <= high) return 'Comfortable';
  if (t <= high + 2.5) return 'Warm';
  return 'Hot';
}

// How far outside its range a room is (0 when comfortable).
const czOff = (t, low, high) => (t == null ? 0 : t < low ? low - t : t > high ? t - high : 0);

// Humidity stays purple: the usual purple when comfortable, paler the drier it
// gets, deeper the more humid (mould risk).
function czHumColour(h, low, high, dry) {
  if (h == null) return KIT_COLOR.humidity;
  const mix = (a, b, f) => { const A = hex(a), B = hex(b); f = Math.max(0, Math.min(1, f)); return `rgb(${A.map((v, k) => Math.round(v + (B[k] - v) * f)).join(',')})`; };
  if (h < low) return mix(KIT_COLOR.humidity, '#ede4ff', (low - h) / Math.max(1, low - dry));
  if (h > high) return mix(KIT_COLOR.humidity, '#6a1bff', (h - high) / 10);
  return KIT_COLOR.humidity;
}

const avg = (list) => (list.length ? list.reduce((a, b) => a + b, 0) / list.length : null);

// Pretend floors for the Design Presets page.
const CZ_DEMO = {
  ground: {
    title: 'Ground Floor',
    rooms: [
      { name: 'Living Room', icon: 'mdi:sofa', type: 'living', t: 21.8, h: 61, ts: [19.1, 18.9, 18.8, 18.9, 19.6, 20.4, 20.9, 20.6, 21.0, 21.4, 21.6, 21.8], hs: [59, 60, 60, 61, 60, 58, 56, 57, 58, 60, 62, 61] },
      { name: 'Entrance', icon: 'mdi:coat-rack', type: 'hall', t: 20.9, ts: [18.4, 18.2, 18.1, 18.3, 18.9, 19.6, 20.1, 20.3, 20.5, 20.7, 20.8, 20.9] },
    ],
  },
  top: {
    title: "Hayley's Floor",
    rooms: [
      { name: "Hayley's Bedroom", icon: 'mdi:bed-king', type: 'bedroom', t: 23.0, h: 48.5, ts: [20.9, 20.7, 20.6, 20.9, 21.4, 22.0, 22.4, 22.6, 22.8, 22.9, 23.0, 23.0], hs: [51, 52, 52, 51, 50, 48, 47, 47.5, 48, 49, 48.5, 48.5] },
      { name: "Hayley's Office", icon: 'mdi:chair-rolling', type: 'office', t: 22.2, h: 58, ts: [19.8, 19.6, 19.5, 19.7, 20.2, 20.8, 21.3, 21.5, 21.8, 22.0, 22.1, 22.2], hs: [59, 60, 60, 59, 58, 56, 55, 56, 57, 58, 58, 58] },
      { name: "Hayley's Landing", icon: 'mdi:stairs', type: 'hall', t: 17.6, ts: [16.4, 16.2, 16.1, 16.3, 16.8, 17.2, 17.5, 17.6, 17.4, 17.5, 17.6, 17.6] },
    ],
  },
  cold: {
    title: 'Cold weather',
    rooms: [
      { name: 'Garage', icon: 'mdi:garage', type: 'hall', t: -1.5, ts: [4, 3, 2, 1, 0.5, 0, -0.5, -1, -1.2, -1.4, -1.5, -1.5] },
      { name: 'Spare Bedroom', icon: 'mdi:bed', type: 'bedroom', t: 13.2, h: 68, ts: [16, 15.5, 15, 14.6, 14.2, 14, 13.8, 13.6, 13.5, 13.3, 13.2, 13.2], hs: [62, 63, 64, 65, 66, 66, 67, 67, 68, 68, 68, 68] },
    ],
  },
};

const TYPE_OPTIONS = Object.entries(CZ_TYPES).map(([value, t]) => ({ value, label: `${t.name} (${t.low}–${t.high}°)` }));

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
                  type: { label: 'Room type (sets the comfortable range)', selector: { select: { mode: 'dropdown', options: TYPE_OPTIONS } } },
                  low: { label: 'Comfortable from (optional, overrides the type)', selector: { number: { min: 0, max: 35, step: 0.5, mode: 'box', unit_of_measurement: '°' } } },
                  high: { label: 'Comfortable to (optional, overrides the type)', selector: { number: { min: 0, max: 40, step: 0.5, mode: 'box', unit_of_measurement: '°' } } },
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
      title: 'Graphs',
      flatten: true,
      schema: [
        { name: 'show_graphs', selector: { boolean: {} }, default: true },
        { name: 'show_limits', selector: { boolean: {} }, default: true },
        { name: 'show_humidity_graph', selector: { boolean: {} }, default: true },
        { name: 'smooth_graphs', selector: { boolean: {} }, default: true },
        { name: 'hours', selector: { number: { min: 1, max: 168, mode: 'box', unit_of_measurement: 'hours' } } },
      ],
    },
    {
      type: 'expandable',
      name: '',
      title: 'Humidity limits (%)',
      flatten: true,
      schema: Object.keys(CZ_HUMIDITY).map((name) => ({ name, selector: { number: { min: 0, max: 100, mode: 'box', unit_of_measurement: '%' } } })),
    },
    {
      type: 'expandable',
      name: '',
      title: 'Demo mode (pretend rooms, for Design Presets)',
      flatten: true,
      schema: [
        { name: 'demo', selector: { boolean: {} } },
        {
          name: 'demo_floor',
          selector: {
            select: {
              mode: 'dropdown',
              options: [
                { value: 'ground', label: 'Ground floor' },
                { value: 'top', label: 'Top floor (warm bedroom, cool landing)' },
                { value: 'cold', label: 'Cold weather (freezing garage)' },
              ],
            },
          },
        },
      ],
    },
  ],
  labels: {
    title: 'Title (e.g. the floor)',
    rooms: 'Rooms and sensors',
    show_graphs: 'A graph for each room',
    show_limits: 'Comfortable range and humidity limits on the graphs',
    show_humidity_graph: 'Humidity line on the graphs',
    smooth_graphs: 'Smooth the graphs (averages jumpy sensor readings)',
    hours: 'Graph length',
    humidity_low: 'Comfortable humidity from',
    humidity_high: 'Comfortable humidity to (above gets deeper purple: mould risk)',
    humidity_dry: 'Too dry below (palest purple)',
    demo: 'Use pretend rooms instead of real sensors',
    demo_floor: 'Pretend floor',
  },
  helpers: {
    rooms: 'Colours follow each room’s comfortable range: teal to lime inside it, blue below, orange to red above, and ice-white with a warning at 0° and below.',
    hours: 'Default 24.',
    humidity_low: 'Defaults: 40–60% comfortable. Humidity stays purple: paler as it gets drier, deeper above 60%.',
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

  _hum(k) {
    const v = this.config[k];
    return v != null && v !== '' ? Number(v) : CZ_HUMIDITY[k];
  }

  _rooms() {
    const c = this.config;
    if (c.demo) {
      const d = CZ_DEMO[c.demo_floor] || CZ_DEMO.ground;
      return d.rooms.map((r) => ({ ...r, ...czRange(r), tPts: kitDemoSeries(r.ts), hPts: r.hs ? kitDemoSeries(r.hs) : null, note: '', hasHumidity: !!r.hs }));
    }
    const s = (id) => id && this._hass && this._hass.states[id];
    const data = (this._hist && this._hist.data) || {};
    return (c.rooms || []).map((r) => {
      const ts = s(r.temperature), hs = s(r.humidity);
      return {
        name: r.name || (ts && ts.attributes.friendly_name) || r.temperature,
        icon: r.icon || (ts && ts.attributes.icon) || 'mdi:thermometer',
        note: r.note || '',
        ...czRange(r),
        t: kitNum(ts),
        h: kitNum(hs),
        tPts: data[r.temperature] || null,
        hPts: r.humidity ? data[r.humidity] || null : null,
        entity: r.temperature,
        hasHumidity: !!r.humidity,
      };
    });
  }

  // The room's graph: comfortable band, temperature coloured by the scale,
  // humidity with dotted limits. Each series on its own scale.
  _graph(r, i, hours, limits, showHum, smooth) {
    const W = 300, H = 56, now = Date.now(), from = now - hours * 3600e3;
    const x = (t) => ((Math.max(from, t) - from) / (now - from)) * W;
    const scrub = [];
    const tRaw = (r.tPts || []).filter((p) => !isNaN(p[1]));
    if (r.t != null) tRaw.push([now, r.t]);
    const tp = smooth ? kitSmooth(tRaw, from, now) : tRaw;
    if (tp.length < 2) return '';
    const tv = tp.map((p) => p[1]);
    let lo = Math.min(...tv), hi = Math.max(...tv);
    if (limits) {
      lo = Math.min(lo, r.low - 1);
      hi = Math.max(hi, r.high + 1);
    }
    lo -= 0.3;
    hi += 0.3;
    const y = (v) => H - 3 - ((v - lo) / (hi - lo || 1)) * (H - 6);
    const id = `cz${i}${Math.random().toString(36).slice(2, 7)}`;
    let stops = '';
    for (let k = 0; k <= 20; k++) stops += `<stop offset="${k * 5}%" stop-color="${czColour(hi - ((hi - lo) * k) / 20, r.low, r.high)}"></stop>`;
    let svg = `<defs><linearGradient id="${id}" x1="0" y1="0" x2="0" y2="${H}" gradientUnits="userSpaceOnUse">${stops}</linearGradient></defs>`;
    if (limits) {
      svg += `<rect x="0" y="${y(r.high).toFixed(1)}" width="${W}" height="${(y(r.low) - y(r.high)).toFixed(1)}" fill="${KIT_COLOR.comfy}" fill-opacity="0.12"></rect>`;
      [r.low, r.high].forEach((v) => {
        svg += `<line x1="0" x2="${W}" y1="${y(v).toFixed(1)}" y2="${y(v).toFixed(1)}" stroke="${KIT_COLOR.comfy}" stroke-opacity="0.55" stroke-dasharray="4 3" vector-effect="non-scaling-stroke"></line>`;
      });
    }
    if (showHum && r.hPts) {
      const hRaw = r.hPts.filter((p) => !isNaN(p[1]));
      if (r.h != null) hRaw.push([now, r.h]);
      const hp = smooth ? kitSmooth(hRaw, from, now) : hRaw;
      if (hp.length >= 2) {
        const hv = hp.map((p) => p[1]);
        const hLow = this._hum('humidity_low'), hHigh = this._hum('humidity_high');
        const hl = Math.min(...hv, limits ? hLow - 5 : Infinity) - 3, hh = Math.max(...hv, limits ? hHigh + 5 : -Infinity) + 3;
        const yh = (v) => H - 3 - ((v - hl) / (hh - hl || 1)) * (H - 6);
        if (limits) {
          [hLow, hHigh].forEach((v) => {
            svg += `<line x1="0" x2="${W}" y1="${yh(v).toFixed(1)}" y2="${yh(v).toFixed(1)}" stroke="${KIT_COLOR.humidity}" stroke-opacity="0.6" stroke-dasharray="1.5 3" vector-effect="non-scaling-stroke"></line>`;
          });
        }
        const humOf = (v) => czHumColour(v, this._hum('humidity_low'), this._hum('humidity_high'), this._hum('humidity_dry'));
        scrub.push({ pts: hp, raw: hRaw, linear: smooth, lo: hl, hi: hh, color: humOf(r.h), colourOf: humOf, format: (v) => `${Math.round(v)}%` });
        svg += `<path d="${kitPath(hp.map((p) => [x(p[0]), yh(p[1])]), smooth)}" fill="none" stroke="${KIT_COLOR.humidity}" stroke-width="1.6" vector-effect="non-scaling-stroke"></path>`;
      }
    }
    scrub.unshift({ pts: tp, raw: tRaw, linear: smooth, lo, hi, color: czColour(r.t, r.low, r.high), colourOf: (v) => czColour(v, r.low, r.high), format: (v) => `${v.toFixed(1)}°` });
    this._scrub[i] = { from, now, height: H, series: scrub };
    const d = kitPath(tp.map((p) => [x(p[0]), y(p[1])]), smooth);
    svg += `<path d="${d} L${W},${H} L0,${H} Z" fill="url(#${id})" fill-opacity="0.14"></path>`;
    svg += `<path d="${d}" fill="none" stroke="url(#${id})" stroke-width="2.2" vector-effect="non-scaling-stroke"></path>`;
    return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" style="display:block; width:100%; height:${H}px;" role="img" aria-label="${r.name}: last ${hours} hours">${svg}</svg>`;
  }

  _render() {
    if (!this._hass || !this.config) return;
    const c = this.config;
    if (!this._built && !this._compact) {
      this.innerHTML = kitShell(`<div class="cz-rooms" style="display:flex; flex-direction:column; gap:10px;"></div>`);
      this._box = this.querySelector('.cz-rooms');
      this._built = true;
    }
    if (this._hist && this._hist.due() && !this._compact) this._hist.load(this._hass);
    const rooms = this._rooms();
    const showGraphs = c.show_graphs !== false;
    const showHum = c.show_humidity_graph !== false;
    const limits = c.show_limits !== false;
    const temps = rooms.map((r) => r.t).filter((v) => v != null);
    const hums = rooms.map((r) => r.h).filter((v) => v != null);
    const t = avg(temps), h = avg(hums);
    // The title takes the colour of the room furthest outside its range.
    const worst = rooms.filter((r) => r.t != null).sort((a, b) => czOff(b.t, b.low, b.high) - czOff(a.t, a.low, a.high))[0];
    const titleColour = !worst ? KIT_COLOR.off : worst.t <= 0 ? FREEZING : czOff(worst.t, worst.low, worst.high) ? czColour(worst.t, worst.low, worst.high) : KIT_COLOR.comfy;
    const title = c.title || (c.demo ? (CZ_DEMO[c.demo_floor] || CZ_DEMO.ground).title : 'Climate');
    // Compact: the floor name and a chip per room, coloured on the scale.
    if (this._compact) {
      return kitCompact(this, {
        name: title,
        color: titleColour,
        value: t != null ? `${t.toFixed(1)}°` : '',
        status: h != null ? `${Math.round(h)}%` : '',
        chips: rooms.map((r) => ({ label: `${r.name} ${r.t != null ? `${r.t.toFixed(1)}°` : '–'}`, color: r.t != null && r.t <= 0 ? FREEZING : czColour(r.t, r.low, r.high) })),
      });
    }
    kitHead(this, title, [t != null ? `${t.toFixed(1)}°` : '', h != null ? `${Math.round(h)}%` : ''].filter(Boolean).join(' · ') + (c.demo ? ' · demo' : ''), titleColour);
    const sig = JSON.stringify([rooms.map((r) => [r.name, r.icon, r.note, r.t, r.h, r.low, r.high]), showGraphs, showHum, limits, this._hist && this._hist.at, c]);
    if (sig === this._sig) return;
    this._sig = sig;
    const hours = Number(c.hours) || 24;
    this._scrub = [];
    const hLow = this._hum('humidity_low'), hHigh = this._hum('humidity_high'), hDry = this._hum('humidity_dry');
    this._box.innerHTML = rooms
      .map((r, i) => {
        const colour = czColour(r.t, r.low, r.high);
        const freezing = r.t != null && r.t <= 0;
        const humWarn = r.h != null && (r.h > hHigh || r.h < hDry);
        const humColour = czHumColour(r.h, hLow, hHigh, hDry);
        const graph = showGraphs ? this._graph(r, i, hours, limits, showHum, c.smooth_graphs !== false) : '';
        const hRange = showGraphs && showHum && r.hPts ? kitRange(r.hPts, r.h, 0, '%') : '';
        return `<div class="cz-room" data-i="${i}" style="display:flex; flex-direction:column; gap:5px;${i ? ' border-top:1px solid var(--divider-color, rgba(127,127,127,0.22)); padding-top:10px;' : ''}">
          ${freezing ? `<div style="display:flex; align-items:center; gap:8px; padding:7px 10px; border-radius:10px; background:${FREEZING}; color:#0b2233; font-size:0.85rem; font-weight:600;">${iconHtml('mdi:snowflake', { size: '20px' })}Freezing: pipes at risk</div>` : ''}
          <div style="display:flex; align-items:center; gap:10px;">
            ${iconHtml(freezing ? 'mdi:snowflake' : r.icon, { size: '22px', style: `color:${colour}; flex:none;` })}
            <div style="flex:1; min-width:0;">
              <div class="cz-name" style="font-size:0.95rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></div>
              <div class="cz-note ck-sub" style="font-size:0.72rem; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"></div>
            </div>
            ${r.h != null ? `<span style="font-size:0.85rem; color:${humColour}; font-weight:${humWarn ? 700 : 400}; font-variant-numeric:tabular-nums;" title="${humWarn ? (r.h > hHigh ? 'Humid: mould risk' : 'Too dry') : 'Humidity'}">${Math.round(r.h * 10) / 10}%</span>` : ''}
            <b style="min-width:54px; text-align:right; font-size:1.15rem; font-variant-numeric:tabular-nums; color:${colour};">${r.t != null ? `${r.t.toFixed(1)}°` : '–'}</b>
          </div>
          ${graph}
          ${graph ? `<div style="display:flex; justify-content:space-between; gap:8px; font-size:0.72rem; color:var(--secondary-text-color);">
              <span style="color:${KIT_COLOR.comfy};">${limits ? `▭ ${r.low}–${r.high}°` : kitRange(r.tPts, r.t, 1, '°')}</span>
              ${hRange ? `<span style="color:${KIT_COLOR.humidity};">${limits ? `┄ ${hLow}–${hHigh}%` : hRange}</span>` : ''}
              <span>last ${hours} h</span>
            </div>` : ''}
          ${showGraphs && !graph && !c.demo ? `<div class="ck-sub" style="font-size:0.72rem;">${this._hist && this._hist.data ? 'No history yet' : 'Loading history…'}</div>` : ''}
        </div>`;
      })
      .join('');
    this._box.querySelectorAll('.cz-room').forEach((el) => {
      const r = rooms[Number(el.dataset.i)];
      el.querySelector('.cz-name').textContent = r.name;
      el.querySelector('.cz-note').textContent = [r.note || r.typeName, czWord(r.t, r.low, r.high)].filter(Boolean).join(' · ');
      const svg = el.querySelector('svg');
      if (svg && this._scrub[Number(el.dataset.i)]) kitScrub(svg, this._scrub[Number(el.dataset.i)]);
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

kitCompactable(ClimateZoneCard, (card) => {
  card._built = false;
  card._sig = null;
});

export function registerClimateZoneCard() {
  if (!customElements.get(`climate-zone-card-editor${SUFFIX}`)) customElements.define(`climate-zone-card-editor${SUFFIX}`, ClimateZoneCardEditor);
  if (!customElements.get(`climate-zone-card${SUFFIX}`)) customElements.define(`climate-zone-card${SUFFIX}`, ClimateZoneCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `climate-zone-card${SUFFIX}`,
    name: `Climate Zone Card${LABEL}`,
    description: 'A floor or zone: temperature and humidity per room, coloured against each room’s comfortable range, with 24-hour graphs',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
