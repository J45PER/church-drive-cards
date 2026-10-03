// Car Card: each car's battery, range and charging, from the car's own
// integration (Vauxhall/Peugeot/… via Stellantis Vehicles, VW group via VW Group
// Connect), wherever it's charging. Whose car is whose is set in Manager ›
// People (kept by the Church Drive integration): with "Only my cars" on, each
// person sees just theirs (a car nobody has is everyone's), and the card hides
// itself for someone with none.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { KIT_COLOR, kitShell, kitHead, kitNum, kitEsc, kitMoreInfo, kitCompact, kitCompactable } from './card-kit.js';

export const CAR_PLATFORMS = ['stellantis_vehicles', 'vag_connect', 'volkswagencarnet', 'volkswagen_we_connect_id'];
const CAR_TEAL = '#00b8d4';

// Every car: { device, name, ids } (one per device with a battery %).
export function carDevices(hass) {
  if (!hass) return [];
  const devs = {};
  Object.values(hass.entities || {}).forEach((e) => {
    if (!CAR_PLATFORMS.includes(e.platform) || !e.device_id || e.hidden) return;
    (devs[e.device_id] = devs[e.device_id] || []).push(e.entity_id);
  });
  return Object.entries(devs)
    .map(([device, ids]) => {
      const d = (hass.devices || {})[device] || {};
      return { device, name: d.name_by_user || d.name || 'Car', ids };
    })
    .filter((c) => carEntities(hass, c.ids).battery);
}

// A car's entities, from its entity ids.
export function carEntities(hass, ids) {
  const st = (id) => (id && hass.states[id]) || null;
  const pick = (dom, re, skip) => ids.find((id) => id.startsWith(`${dom}.`) && re.test(id) && !(skip && skip.test(id)) && st(id)) || '';
  return {
    battery:
      ids.find((id) => id.startsWith('sensor.') && st(id) && st(id).attributes.device_class === 'battery' && st(id).attributes.unit_of_measurement === '%' && !/_(service|12v|aux|soh|key)/i.test(id)) || '',
    range: pick('sensor', /(electric_|battery_)?range$/, /fuel|combustion|total/),
    fuel: pick('sensor', /_fuel(_level)?$/),
    fuelRange: pick('sensor', /fuel_range$|combustion_range$/),
    plugged: pick('binary_sensor', /plug/, /lock/),
    charging: pick('binary_sensor', /charging$/),
    end: pick('sensor', /charging_end|charge_end|charging_time_left|remaining_charging/),
    tracker: pick('device_tracker', /./),
  };
}

const DEMO_CARS = [
  { name: 'Electric car', battery: 64, range: '142 mi', fuel: null, fuelRange: '', plugged: true, charging: true, end: 'Full by 06:30', where: 'Home' },
  { name: 'Hybrid', battery: 35, range: '9 mi', fuel: 62, fuelRange: '261 mi', plugged: false, charging: false, end: '', where: 'Away' },
];

export const CarCardEditor = createFormEditor({
  schema: (config) => [
    { name: 'title', selector: { text: {} } },
    ...(config.demo
      ? []
      : [
          { name: 'only_mine', selector: { boolean: {} }, default: true },
          {
            name: 'cars',
            selector: {
              object: {
                multiple: true,
                label_field: 'name',
                fields: {
                  device: { label: 'Car', required: true, selector: { device: { filter: CAR_PLATFORMS.map((integration) => ({ integration })) } } },
                  name: { label: 'Name (optional)', selector: { text: {} } },
                },
              },
            },
          },
        ]),
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: {
    title: 'Title (optional)',
    only_mine: 'Only show the signed-in person their own cars',
    cars: 'Cars to show (empty for every car)',
    demo: 'Show pretend cars (for Design Presets)',
  },
  helpers: {
    cars: "Cars come from their own integrations (Stellantis Vehicles for Vauxhall, VW Group Connect for VW). Whose car is whose is set in Manager › People.",
  },
});

export class CarCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
    this._sig = null;
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
  }

  // The signed-in person's entity id.
  _me() {
    const u = this._hass && this._hass.user;
    if (!u) return '';
    return Object.keys(this._hass.states).find((x) => x.startsWith('person.') && this._hass.states[x].attributes.user_id === u.id) || '';
  }

  _cars() {
    const c = this.config;
    if (c.demo) return DEMO_CARS;
    const hass = this._hass;
    const all = carDevices(hass);
    // Owners from the Church Drive people sensor ({ people: [{ entity_id, cars }] }).
    const ppl = (hass.states['sensor.church_drive_people'] || { attributes: {} }).attributes.people || [];
    const owners = (dev) => ppl.filter((p) => (p.cars || []).includes(dev)).map((p) => p.entity_id);
    const list = Array.isArray(c.cars) && c.cars.length
      ? c.cars
          .map((x) => {
            const d = all.find((y) => y.device === x.device);
            return d ? { ...d, name: x.name || d.name, people: x.people && x.people.length ? x.people : owners(d.device) } : null;
          })
          .filter(Boolean)
      : all.map((d) => ({ ...d, people: owners(d.device) }));
    const me = this._me();
    const mine = c.only_mine !== false ? list.filter((x) => !x.people.length || x.people.includes(me)) : list;
    const st = (id) => (id && hass.states[id]) || null;
    const miles = (id) => {
      const s = st(id);
      return s && kitNum(s) != null ? `${Math.round(kitNum(s))} ${s.attributes.unit_of_measurement || ''}`.trim() : '';
    };
    return mine.map((x) => {
      const e = carEntities(hass, x.ids);
      const t = st(e.tracker);
      const where = !t || ['unknown', 'unavailable'].includes(t.state) ? '' : t.state === 'home' ? 'Home' : t.state === 'not_home' ? 'Away' : t.state;
      const end = st(e.end);
      const charging = !!(st(e.charging) && st(e.charging).state === 'on');
      let endText = '';
      if (charging && end && !['unknown', 'unavailable', ''].includes(end.state)) {
        const when = new Date(end.state);
        endText = isNaN(when) ? `${end.state}${end.attributes.unit_of_measurement ? ` ${end.attributes.unit_of_measurement}` : ''} left` : `Full by ${when.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })}`;
      }
      return {
        name: x.name,
        battery: kitNum(st(e.battery)),
        range: miles(e.range),
        fuel: kitNum(st(e.fuel)),
        fuelRange: miles(e.fuelRange),
        plugged: !!(st(e.plugged) && st(e.plugged).state === 'on'),
        charging,
        end: endText,
        where,
        entity: e.battery,
      };
    });
  }

  _render() {
    if (!this._hass) return;
    const c = this.config;
    const cars = this._cars();
    // Nobody's cars here for this person: hide (except while editing the dashboard).
    const empty = !cars.length;
    this.style.display = empty && !this.editMode ? 'none' : '';
    const charging = cars.filter((x) => x.charging).length;
    const word = empty ? 'No cars' : charging ? `${charging} charging` : cars.length === 1 ? (cars[0].plugged ? 'Plugged in' : cars[0].where || '') : `${cars.length} cars`;
    if (this._compact) {
      const x = cars[0];
      return kitCompact(this, {
        name: c.title || (cars.length === 1 ? x.name : 'Cars'),
        color: charging ? CAR_TEAL : KIT_COLOR.off,
        value: x && x.battery != null ? `${Math.round(x.battery)}%` : '',
        valueColor: CAR_TEAL,
        status: word + (c.demo ? ' · demo' : ''),
      });
    }
    if (!this._built) {
      this.innerHTML = kitShell(`<div class="cc-list"></div>`, `
        .cc-list { display:flex; flex-direction:column; gap:14px; }
        .cc-car { display:flex; flex-direction:column; gap:6px; cursor:pointer; }
        .cc-top { display:grid; grid-template-columns:auto 1fr auto; align-items:center; gap:10px; }
        .cc-name { font-size:.95rem; font-weight:600; display:flex; gap:6px; align-items:center; min-width:0; }
        .cc-name span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
        .cc-name em { font-style:normal; font-size:.68rem; font-weight:700; padding:1px 7px; border-radius:999px; white-space:nowrap; }
        .cc-pc { font-size:1.6rem; font-weight:300; font-variant-numeric:tabular-nums; line-height:1; }
        .cc-bar { height:8px; border-radius:4px; background:rgba(127,127,127,.2); overflow:hidden; }
        .cc-bar i { display:block; height:100%; border-radius:4px; transition:width .4s; }
        .cc-sub { font-size:.75rem; color:var(--secondary-text-color); display:flex; flex-wrap:wrap; gap:4px 12px; }`);
      this._list = this.querySelector('.cc-list');
      this._list.addEventListener('click', (ev) => {
        const el = ev.target.closest('[data-id]');
        if (el && el.dataset.id && !this.config.demo) kitMoreInfo(this, el.dataset.id);
      });
      this._built = true;
    }
    kitHead(this, c.title || 'Cars', word + (c.demo ? ' · demo' : ''), CAR_TEAL);
    const sig = JSON.stringify(cars);
    if (sig === this._sig) return;
    this._sig = sig;
    const barCol = (p) => (p == null ? KIT_COLOR.off : p < 20 ? '#ef5350' : p < 40 ? '#ffa726' : CAR_TEAL);
    this._list.innerHTML = empty
      ? `<div class="ck-sub">No cars to show here. Cars come from their own integrations, and are given to people in Manager › People.</div>`
      : cars
          .map((x) => {
            const tag = x.charging ? ['Charging', CAR_TEAL] : x.plugged ? ['Plugged in', '#42a5f5'] : x.where ? [x.where, 'var(--secondary-text-color)'] : null;
            const sub = [x.range ? `${x.range} electric` : '', x.fuel != null ? `Fuel ${Math.round(x.fuel)}%${x.fuelRange ? ` · ${x.fuelRange}` : ''}` : '', x.end].filter(Boolean);
            return `<div class="cc-car" data-id="${kitEsc(x.entity || '')}" role="button" tabindex="0" aria-label="${kitEsc(x.name)}">
              <div class="cc-top">${iconHtml(x.charging ? 'mdi:car-electric' : 'mdi:car', { size: '24px', style: `color:${x.plugged || x.charging ? CAR_TEAL : 'var(--secondary-text-color)'};` })}
                <div class="cc-name"><span>${kitEsc(x.name)}</span>${tag ? `<em style="background:color-mix(in srgb, ${tag[1]} 20%, transparent); color:${tag[1]};">${kitEsc(tag[0])}</em>` : ''}</div>
                <div class="cc-pc" style="color:${barCol(x.battery)};">${x.battery == null ? '–' : `${Math.round(x.battery)}%`}</div></div>
              <div class="cc-bar"><i style="width:${Math.max(0, Math.min(100, x.battery || 0))}%; background:${barCol(x.battery)};"></i></div>
              ${sub.length ? `<div class="cc-sub">${sub.map((s) => `<span>${kitEsc(s)}</span>`).join('')}</div>` : ''}
            </div>`;
          })
          .join('');
    hydrateIcons(this);
  }

  getCardSize() {
    return 3;
  }

  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`car-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return {};
  }
}

kitCompactable(CarCard, (card) => {
  card._built = false;
  card._sig = null;
});

export function registerCarCard() {
  if (!customElements.get(`car-card-editor${SUFFIX}`)) customElements.define(`car-card-editor${SUFFIX}`, CarCardEditor);
  if (!customElements.get(`car-card${SUFFIX}`)) customElements.define(`car-card${SUFFIX}`, CarCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `car-card${SUFFIX}`,
    name: `Car Card${LABEL}`,
    description: "Each car's battery, range and charging, from the car's own integration; cars can be given to people",
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
