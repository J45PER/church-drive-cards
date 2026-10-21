// Watch Settings Card (Manager): what the Wear OS companion app shows.
//
// Choose which pages the watch has and in what order (Alarm, Climate, Lights, Cleaning, Car charger), then which items each page shows
// (which thermostats and fans, which floors, rooms and zones, which cars, which faces of the vacuum and charger), how Lights starts, and
// Clive's toggles. The settings are kept by the Church Drive integration (watch.py); the watch reads them whenever it connects and when
// they change. Everything is shown unless it is hidden here, so a new device turns up on the watch by itself. Only administrators can change them.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitShell, kitHead, kitEsc } from './card-kit.js';
import { PAGES, LIGHTS_TOP, ALARM_MODES, CLEANING_FACES, CAR_FACES, CLIVE_FLAGS, normalise, setPageOn, movePage, isHidden, setItemShown, setLightsTop, setCliveFlag, setCar, parseLights, carCandidates, climateDevices } from './watch-logic.js';

const WS = 'church_drive/watch';
const EVENT = 'church_drive_watch_changed';

const WS_CSS = `
  .wc-boxes { columns:340px; column-gap:12px; }
  .wc-box { break-inside:avoid; margin-bottom:12px; padding:10px 12px 12px; border-radius:14px; background:rgba(127,127,127,0.08); display:flex; flex-direction:column; gap:6px; }
  .wc-gh { font-size:0.7rem; letter-spacing:0.07em; text-transform:uppercase; font-weight:700; color:var(--secondary-text-color); }
  .wc-page { display:flex; align-items:center; gap:8px; padding:4px 0; border-top:1px solid var(--divider-color, rgba(127,127,127,0.18)); }
  .wc-page:first-of-type { border-top:none; }
  .wc-name { flex:1; min-width:0; font-size:0.9rem; font-weight:600; }
  .wc-name small { display:block; font-weight:400; color:var(--secondary-text-color); font-size:0.75rem; }
  .wc-sw { flex:none; border:none; border-radius:999px; padding:5px 12px; font:inherit; font-size:0.78rem; font-weight:700; cursor:pointer; background:rgba(127,127,127,0.18); color:var(--primary-text-color); }
  .wc-sw[aria-pressed="true"] { color:#fff; }
  .wc-sw:disabled { cursor:default; opacity:.7; }
  .wc-mv { flex:none; border:none; width:30px; height:30px; border-radius:9px; cursor:pointer; background:rgba(127,127,127,0.18); color:var(--primary-text-color); display:flex; align-items:center; justify-content:center; }
  .wc-mv:disabled { opacity:.3; cursor:default; }
  .wc-items { display:flex; flex-direction:column; }
  .wc-item { display:flex; align-items:center; gap:8px; padding:5px 0; font-size:0.85rem; cursor:pointer; }
  .wc-item input { width:18px; height:18px; flex:none; }
  .wc-item small { color:var(--secondary-text-color); margin-left:4px; }
  .wc-sub { font-size:0.8rem; font-weight:700; margin-top:6px; }
  .wc-chips { display:flex; flex-wrap:wrap; gap:6px; }
  .wc-chip { border:none; border-radius:999px; padding:5px 11px; font:inherit; font-size:0.78rem; font-weight:600; cursor:pointer; background:rgba(127,127,127,0.18); color:var(--primary-text-color); }
  .wc-chip[aria-pressed="true"] { color:#fff; }
  .wc-chip:disabled { cursor:default; }
  .wc-sw:focus-visible, .wc-mv:focus-visible, .wc-chip:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
  .wc-msg { font-size:0.8rem; color:#ffa726; }
`;

function wcDemo() {
  return {
    config: normalise({ hidden: { climate: ['fan.bedroom'] }, clive: { follow: true } }),
    states: {
      'climate.downstairs': { attributes: { friendly_name: 'Downstairs' } },
      'climate.lounge_air_con': { attributes: { friendly_name: 'Lounge Air Con' } },
      'fan.bedroom': { attributes: { friendly_name: 'Bedroom Fan' } },
      'fan.air_purifier': { attributes: { friendly_name: 'Air Purifier' } },
      'binary_sensor.astra_battery_plugged': { attributes: {} },
      'sensor.astra_battery': { attributes: { friendly_name: 'Astra Battery' } },
    },
    floors: [{ name: 'Ground Floor', rooms: [{ head: 'light.kitchen', title: 'Kitchen', zones: [{ entity: 'light.kitchen_island', name: 'Island' }] }, { head: 'light.hall', title: 'Hall', zones: [] }] }],
  };
}

export const WatchSettingsCardEditor = createFormEditor({
  schema: () => [
    { name: 'title', selector: { text: {} } },
    { name: 'color', selector: { ui_color: {} } },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: { title: 'Title (optional)', color: 'Colour', demo: 'Show pretend devices (for Design Presets; changes are switched off)' },
  helpers: { color: 'Default blue (#5c6bc0).' },
});

export class WatchSettingsCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
    this._sig = null;
    this._demo = this.config.demo ? wcDemo() : null;
    this._cfg = this._demo ? this._demo.config : this._cfg || null;
  }

  set hass(hass) {
    this._hass = hass;
    if (!this.config.demo) {
      if (!this._watching && hass.connection && hass.connection.subscribeEvents) {
        this._watching = true;
        Promise.resolve(hass.connection.subscribeEvents(() => this._load(), EVENT))
          .then((unsub) => {
            this._unsub = unsub;
          })
          .catch(() => {
            this._watching = false;
          });
      }
      if (!this._loaded) {
        this._loaded = true;
        this._load();
      }
      this._lookups(hass);
    }
    this._render();
  }

  disconnectedCallback() {
    if (this._unsub) this._unsub();
    this._unsub = null;
    this._watching = false;
  }

  async _load() {
    try {
      this._cfg = normalise(await this._hass.connection.sendMessagePromise({ type: WS }));
      this._error = '';
    } catch (err) {
      this._error = "Church Drive's watch settings aren't running. Update Church Drive and restart Home Assistant.";
    }
    this._sig = null;
    this._render();
  }

  // The rooms and zones come from the Mobile dashboard, the same place the watch reads them.
  async _lookups(hass) {
    if (this._floorsAsked) return;
    this._floorsAsked = true;
    try {
      const dash = await hass.connection.sendMessagePromise({ type: 'lovelace/config', url_path: 'dashboard-mobile' });
      this._floors = parseLights(dash, (id) => ((hass.states[id] || {}).attributes || {}).friendly_name || '');
    } catch (err) {
      this._floors = [];
    }
    this._sig = null;
    this._render();
  }

  _colour() {
    return this.config.color || '#5c6bc0';
  }

  _canEdit() {
    return !!(this.config.demo || (this._hass && this._hass.user && this._hass.user.is_admin));
  }

  _states() {
    return this._demo ? this._demo.states : (this._hass && this._hass.states) || {};
  }

  async _save(next) {
    this._cfg = next;
    this._msg = '';
    this._sig = null;
    this._render();
    if (this.config.demo) return;
    try {
      this._cfg = normalise(await this._hass.connection.sendMessagePromise({ type: `${WS}/set`, config: next }));
    } catch (err) {
      this._msg = (err && err.message) || String(err);
    }
    this._sig = null;
    this._render();
  }

  _build() {
    this.innerHTML = kitShell(`<div class="wc-body"><div class="wc-boxes"></div></div>`, WS_CSS);
    this.addEventListener('click', (ev) => {
      const b = ev.target.closest('[data-act]');
      if (!b || b.disabled || !this._cfg || !this._canEdit()) return;
      const [act, a, c] = b.dataset.act.split('|');
      const cfg = this._cfg;
      if (act === 'page') this._save(setPageOn(cfg, a, b.getAttribute('aria-pressed') !== 'true'));
      else if (act === 'move') this._save(movePage(cfg, a, Number(c)));
      else if (act === 'top') this._save(setLightsTop(cfg, a));
      else if (act === 'clive') this._save(setCliveFlag(cfg, a, b.getAttribute('aria-pressed') !== 'true'));
    });
    this.addEventListener('change', (ev) => {
      const i = ev.target.closest('input[data-item]');
      if (!i || !this._cfg || !this._canEdit()) return;
      const [page, item] = i.dataset.item.split('|');
      if (page === 'cars') {
        const car = carCandidates(this._states()).find((c) => c.battery === item);
        if (car) this._save(setCar(this._cfg, car, i.checked));
      } else this._save(setItemShown(this._cfg, page, item, i.checked));
    });
    this._built = true;
  }

  _items(page, list) {
    const edit = this._canEdit();
    return `<div class="wc-items">${list.map((it) => `<label class="wc-item"><input type="checkbox" data-item="${kitEsc(page)}|${kitEsc(it.id)}" ${isHidden(this._cfg, page, it.id) ? '' : 'checked'} ${edit ? '' : 'disabled'}>${kitEsc(it.label)}${it.sub ? `<small>${kitEsc(it.sub)}</small>` : ''}</label>`).join('')}</div>`;
  }

  _pageBox(p, colour) {
    const edit = this._canEdit();
    const cfg = this._cfg;
    let body = '';
    if (p.id === 'alarm') body = `<div class="wc-sub">Modes shown</div>${this._items('alarm', ALARM_MODES)}`;
    else if (p.id === 'climate') {
      const devs = climateDevices(this._states());
      body = devs.length ? `<div class="wc-sub">Devices shown</div>${this._items('climate', devs.map((d) => ({ id: d.id, label: d.name, sub: d.kind })))}` : '<div class="ck-sub">No thermostats, air con or fans found.</div>';
    } else if (p.id === 'lights') {
      const floors = this._floors || (this._demo && this._demo.floors) || [];
      const top = `<div class="wc-sub">Starts with</div><div class="wc-chips">${LIGHTS_TOP.map((t) => `<button type="button" class="wc-chip" data-act="top|${t.id}" aria-pressed="${cfg.lights_top === t.id}" ${edit ? '' : 'disabled'} style="${cfg.lights_top === t.id ? `background:${colour};` : ''}">${kitEsc(t.label)}</button>`).join('')}</div>`;
      const rows = [];
      for (const f of floors) {
        for (const r of f.rooms) {
          rows.push({ id: r.head, label: r.title, sub: f.name || 'room' });
          for (const z of r.zones) rows.push({ id: z.entity, label: z.name, sub: 'zone' });
        }
      }
      body = top + (rows.length ? `<div class="wc-sub">Shown on the watch</div>${this._items('lights', rows)}` : '<div class="ck-sub">No rooms found on the Mobile dashboard yet.</div>');
    } else if (p.id === 'cleaning') body = `<div class="wc-sub">Faces shown</div>${this._items('cleaning', CLEANING_FACES)}`;
    else if (p.id === 'car') {
      const cars = carCandidates(this._states());
      const carsBox = cars.length
        ? `<div class="wc-sub">Cars whose battery can show on the ring</div><div class="wc-items">${cars.map((c) => `<label class="wc-item"><input type="checkbox" data-item="cars|${kitEsc(c.battery)}" ${cfg.cars.some((x) => x.battery === c.battery) ? 'checked' : ''} ${edit ? '' : 'disabled'}>${kitEsc(c.name)}</label>`).join('')}</div><div class="ck-sub">The battery only shows when exactly one of these is plugged in; otherwise the ring is coloured by mode.</div>`
        : '';
      body = `<div class="wc-sub">Faces shown</div>${this._items('car', CAR_FACES)}${carsBox}`;
    }
    const meta = PAGES.find((x) => x.id === p.id);
    const i = cfg.pages.findIndex((x) => x.id === p.id);
    return `<div class="wc-box">
      <div class="wc-page">${iconHtml(meta.icon, { size: '22px' })}
        <div class="wc-name">${kitEsc(meta.label)}<small>${kitEsc(meta.what)}</small></div>
        <button type="button" class="wc-mv" data-act="move|${p.id}|-1" aria-label="Move ${kitEsc(meta.label)} up" ${edit && i > 0 ? '' : 'disabled'}>${iconHtml('mdi:chevron-up', { size: '18px' })}</button>
        <button type="button" class="wc-mv" data-act="move|${p.id}|1" aria-label="Move ${kitEsc(meta.label)} down" ${edit && i < cfg.pages.length - 1 ? '' : 'disabled'}>${iconHtml('mdi:chevron-down', { size: '18px' })}</button>
        <button type="button" class="wc-sw" data-act="page|${p.id}" aria-pressed="${p.on}" ${edit ? '' : 'disabled'} style="${p.on ? `background:${colour};` : ''}">${p.on ? 'On' : 'Off'}</button></div>
      ${p.on ? body : '<div class="ck-sub">Not on the watch.</div>'}</div>`;
  }

  _render() {
    if (!this._hass && !this.config.demo) return;
    if (!this._built) this._build();
    const colour = this._colour();
    const head = this.querySelector('.ck-headrow');
    if (head) {
      head.style.display = this.config.title ? '' : 'none';
      if (this.config.title) kitHead(this, this.config.title, '', colour);
    }
    const sig = JSON.stringify([this._cfg, this._error, this._msg, colour, this._canEdit(), this._floors, Object.keys(this._states()).length]);
    if (sig === this._sig) return;
    this._sig = sig;
    const box = this.querySelector('.wc-boxes');
    if (this._error || !this._cfg) {
      box.innerHTML = `<div class="ck-sub" style="line-height:1.5;">${kitEsc(this._error || 'Loading…')}</div>`;
      return;
    }
    const clive = `<div class="wc-box"><div class="wc-gh">Clive on the watch</div><div class="wc-chips">${CLIVE_FLAGS.map((f) => `<button type="button" class="wc-chip" data-act="clive|${f.id}" aria-pressed="${!!this._cfg.clive[f.id]}" ${this._canEdit() ? '' : 'disabled'} style="${this._cfg.clive[f.id] ? `background:${colour};` : ''}">${kitEsc(f.label)}</button>`).join('')}</div><div class="ck-sub">The starting point for these toggles; each person can still change them on the watch.</div></div>`;
    box.innerHTML = `<div class="wc-box"><div class="wc-gh">Pages on the watch, in order</div><div class="ck-sub">Switch a page off to hide it, or move it up and down. Under each, untick anything you don't want there; new devices appear by themselves.</div></div>${this._cfg.pages.map((p) => this._pageBox(p, colour)).join('')}${clive}${this._msg ? `<div class="wc-msg" role="alert">${kitEsc(this._msg)}</div>` : ''}${this._canEdit() ? '' : '<div class="ck-sub">Only administrators can change these.</div>'}`;
    hydrateIcons(this);
  }

  getCardSize() {
    return 12;
  }

  static getConfigElement() {
    return document.createElement(`watch-settings-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { title: 'Watch' };
  }
}

export function registerWatchSettingsCard() {
  if (!customElements.get(`watch-settings-card-editor${SUFFIX}`)) customElements.define(`watch-settings-card-editor${SUFFIX}`, WatchSettingsCardEditor);
  if (!customElements.get(`watch-settings-card${SUFFIX}`)) customElements.define(`watch-settings-card${SUFFIX}`, WatchSettingsCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `watch-settings-card${SUFFIX}`,
    name: `Watch Settings Card${LABEL}`,
    description: 'Choose the pages the Wear OS companion shows, their order and the items on each page, plus Clive\'s toggles',
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
