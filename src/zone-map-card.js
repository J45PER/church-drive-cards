// Zone Map Card (Manager's Locations): Home Assistant's zones on a satellite
// (or street) map, to see and change them. Search an address or postcode,
// "+ Add zone" then tap the map, tap a zone to name, resize (drag its white
// handle) or move it (drag it), Save or Delete. Changes go into Home
// Assistant's own zones (zone/create, zone/update, zone/delete; Home through
// config/core/update), so Settings › Zones and people's places keep working.
// Only administrators can change zones.
//
// The Satellite / Street buttons also set the style of every other map in
// Home Assistant on this device (see map-style.js). Imagery from Esri; place
// search from OpenStreetMap (Nominatim).

import * as L from 'leaflet/dist/leaflet-src.esm.js';
import LEAFLET_CSS from 'leaflet/dist/leaflet.css';
import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { kitEsc } from './card-kit.js';
import { MAP_LAYERS, mapStyle, setMapStyle } from './map-style.js';

const ATTR = 'Imagery &copy; Esri, Maxar, Earthstar Geographics &middot; Search &copy; OpenStreetMap';
const HOME = '#4caf50';
const PALETTE = ['#26a69a', '#ffb300', '#ab47bc', '#42a5f5', '#ef5350', '#8d6e63'];

const ZM_CSS = `
  ${LEAFLET_CSS}
  .zm-wrap { position:relative; border-radius:16px; overflow:hidden; height:var(--zm-h, 420px); background:#2a2f2a; }
  .zm-map { position:absolute; inset:0; }
  .zm-map .leaflet-control-attribution { font-size:9px; background:rgba(0,0,0,0.45); color:#ddd; }
  .zm-map .leaflet-control-attribution a { color:#ddd; }
  .zm-search { position:absolute; z-index:500; top:10px; left:10px; right:150px; }
  .zm-search input { width:100%; box-sizing:border-box; height:38px; border-radius:12px; border:none; padding:0 12px 0 34px; font:inherit; font-size:0.85rem;
    background:rgba(31,33,40,0.92); color:#eee; outline:none; }
  .zm-search .zm-glass { position:absolute; left:10px; top:10px; color:#9aa0ad; }
  .zm-results { margin-top:4px; border-radius:12px; overflow:hidden; background:rgba(31,33,40,0.96); }
  .zm-results button { display:block; width:100%; text-align:left; border:none; background:transparent; color:#eee; font:inherit; font-size:0.8rem; padding:8px 12px; cursor:pointer; }
  .zm-results button:hover { background:rgba(255,255,255,0.08); }
  .zm-layers { position:absolute; z-index:500; top:10px; right:10px; display:flex; border-radius:12px; overflow:hidden; }
  .zm-layers button, .zm-zoom button, .zm-add { border:none; cursor:pointer; font:inherit; font-size:0.78rem; font-weight:700; color:#eee; background:rgba(31,33,40,0.92); }
  .zm-layers button { padding:10px 11px; }
  .zm-layers button.on { background:#26a69a; color:#fff; }
  .zm-zoom { position:absolute; z-index:500; right:10px; bottom:24px; display:flex; flex-direction:column; gap:6px; }
  .zm-zoom button { width:36px; height:36px; border-radius:10px; display:flex; align-items:center; justify-content:center; }
  .zm-add { position:absolute; z-index:500; left:10px; bottom:24px; border-radius:12px; padding:10px 14px; background:#26a69a; color:#fff; }
  .zm-add.on { background:#ffb300; color:#221; }
  .zm-tag { background:rgba(0,0,0,0.65); color:#fff; border:none; border-radius:8px; box-shadow:none; font-weight:800; font-size:11px; padding:2px 7px; }
  .zm-tag::before { display:none; }
  .zm-handle { width:14px !important; height:14px !important; margin:-7px 0 0 -7px !important; border-radius:50%; background:#fff; border:2px solid #26a69a; box-sizing:border-box; cursor:ew-resize; }
  .zm-mover { width:22px !important; height:22px !important; margin:-11px 0 0 -11px !important; border-radius:50%; background:rgba(255,255,255,0.9); border:2px solid #26a69a; box-sizing:border-box; cursor:move; }
  .zm-edit { display:flex; flex-wrap:wrap; gap:10px; align-items:center; margin-top:10px; padding:10px; border-radius:14px; background:rgba(127,127,127,0.1); font-size:0.85rem; }
  .zm-edit input { flex:1 1 160px; min-width:0; height:36px; box-sizing:border-box; border-radius:10px; border:1px solid var(--divider-color, rgba(127,127,127,0.3));
    background:var(--secondary-background-color, rgba(127,127,127,0.12)); color:var(--primary-text-color); font:inherit; padding:0 10px; }
  .zm-size { color:var(--secondary-text-color); white-space:nowrap; }
  .zm-btn { border:none; cursor:pointer; font:inherit; font-size:0.82rem; font-weight:700; border-radius:999px; padding:8px 14px; background:rgba(127,127,127,0.16); color:var(--primary-text-color); }
  .zm-btn.save { background:#26a69a; color:#fff; }
  .zm-btn.del { background:rgba(229,57,53,0.18); color:#ef9a9a; }
  .zm-hint { margin-top:8px; font-size:0.75rem; color:var(--secondary-text-color); }
`;

export const ZoneMapCardEditor = createFormEditor({
  schema: () => [
    { name: 'height', selector: { number: { min: 240, max: 900, step: 20, mode: 'box', unit_of_measurement: 'px' } } },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: { height: 'Map height (default 420)', demo: 'Show pretend zones (for Design Presets)' },
  helpers: { height: 'Zones are Home Assistant\'s own; changes here show in Settings › Zones too.' },
});

export class ZoneMapCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
  }

  set hass(hass) {
    const first = !this._hass;
    this._hass = hass;
    if (!this._built) this._build();
    // Redraw when zones change (not on every state change).
    const sig = Object.keys(hass.states)
      .filter((id) => id.startsWith('zone.'))
      .map((id) => {
        const a = hass.states[id].attributes;
        return `${id}:${a.latitude},${a.longitude},${a.radius},${a.friendly_name}`;
      })
      .join('|');
    if (sig !== this._zsig) {
      this._zsig = sig;
      this._loadIds().then(() => this._draw(first));
    }
  }

  connectedCallback() {
    if (this._map) setTimeout(() => this._map.invalidateSize(), 50);
    this._onStyle = () => this._setLayer();
    window.addEventListener('cd-map-style', this._onStyle);
  }

  disconnectedCallback() {
    window.removeEventListener('cd-map-style', this._onStyle);
  }

  _admin() {
    return !!(this.config.demo || (this._hass && this._hass.user && this._hass.user.is_admin));
  }

  _build() {
    const c = this.config;
    this.innerHTML = `<style>${ZM_CSS}</style>
      <div class="zm-wrap" style="--zm-h:${Number(c.height) || 420}px;">
        <div class="zm-map"></div>
        <div class="zm-search"><span class="zm-glass">${iconHtml('mdi:magnify', { size: '18px' })}</span><input placeholder="Search an address, postcode or place…" aria-label="Search the map"><div class="zm-results"></div></div>
        <div class="zm-layers"><button data-style="satellite">Satellite</button><button data-style="street">Street</button></div>
        <div class="zm-zoom"><button data-zoom="in" aria-label="Zoom in">+</button><button data-zoom="out" aria-label="Zoom out">−</button><button data-zoom="fit" aria-label="Show all zones">${iconHtml('mdi:crosshairs-gps', { size: '18px' })}</button></div>
        ${this._admin() ? '<button class="zm-add">+ Add zone</button>' : ''}
      </div>
      <div class="zm-edit" style="display:none;"></div>
      <div class="zm-hint">${this._admin() ? 'Tap a zone to rename, move (drag its centre) or resize (drag the white dot) it.' : ''}</div>`;
    hydrateIcons(this);
    const el = this.querySelector('.zm-map');
    this._map = L.map(el, { zoomControl: false, attributionControl: true, worldCopyJump: true }).setView([53.2, -1.21], 13);
    this._map.attributionControl.setPrefix(false);
    this._setLayer();
    this._layer = L.layerGroup().addTo(this._map);
    this._map.on('click', (ev) => this._mapClick(ev));
    this.querySelector('.zm-layers').addEventListener('click', (ev) => {
      const b = ev.target.closest('[data-style]');
      if (b) setMapStyle(b.dataset.style);
    });
    this.querySelector('.zm-zoom').addEventListener('click', (ev) => {
      const b = ev.target.closest('[data-zoom]');
      if (!b) return;
      if (b.dataset.zoom === 'in') this._map.zoomIn();
      else if (b.dataset.zoom === 'out') this._map.zoomOut();
      else this._fit();
    });
    const add = this.querySelector('.zm-add');
    if (add) {
      add.addEventListener('click', () => {
        this._adding = !this._adding;
        add.classList.toggle('on', this._adding);
        add.textContent = this._adding ? 'Tap the map…' : '+ Add zone';
      });
    }
    const input = this.querySelector('.zm-search input');
    input.addEventListener('input', () => {
      clearTimeout(this._searchTimer);
      this._searchTimer = setTimeout(() => this._search(input.value), 500);
    });
    this.querySelector('.zm-results').addEventListener('click', (ev) => {
      const b = ev.target.closest('[data-lat]');
      if (!b) return;
      this._map.setView([Number(b.dataset.lat), Number(b.dataset.lon)], 17);
      this.querySelector('.zm-results').innerHTML = '';
      input.value = b.textContent;
    });
    // Stop the map dragging while using the controls.
    this.querySelectorAll('.zm-search, .zm-layers, .zm-zoom, .zm-add').forEach((n) => {
      L.DomEvent.disableClickPropagation(n);
      L.DomEvent.disableScrollPropagation(n);
    });
    this.querySelector('.zm-edit').addEventListener('click', (ev) => this._editClick(ev));
    if (window.ResizeObserver) new ResizeObserver(() => this._map.invalidateSize()).observe(el);
    this._built = true;
  }

  _setLayer() {
    if (!this._map) return;
    const style = mapStyle() === 'street' ? 'street' : 'satellite';
    (this._tiles || []).forEach((t) => this._map.removeLayer(t));
    const spec = MAP_LAYERS[style];
    this._tiles = [L.tileLayer(spec.base, { maxNativeZoom: 19, maxZoom: 20, attribution: ATTR }), ...spec.labels.map((u) => L.tileLayer(u, { maxNativeZoom: 19, maxZoom: 20, zIndex: 5 }))];
    this._tiles.forEach((t) => t.addTo(this._map));
    this.querySelectorAll('.zm-layers button').forEach((b) => b.classList.toggle('on', b.dataset.style === style));
  }

  // Zone ids for editing (zone/list), matched to the zone entities by name.
  async _loadIds() {
    if (this.config.demo || !this._admin()) return;
    try {
      this._ids = await this._hass.callWS({ type: 'zone/list' });
    } catch (err) {
      this._ids = [];
    }
  }

  // Every zone: { entity, id, name, lat, lon, r, home, colour }.
  _zones() {
    if (this.config.demo) {
      return [
        { entity: 'zone.home', id: 'home', name: 'Home', lat: 53.19969, lon: -1.21659, r: 100, home: true, colour: HOME, editable: true },
        { entity: 'zone.frasers', id: 'f', name: 'Frasers Group', lat: 53.19704, lon: -1.20521, r: 234, colour: PALETTE[0], editable: true },
        { entity: 'zone.po', id: 'p', name: 'Shirebrook Post Office', lat: 53.20390, lon: -1.21259, r: 24, colour: PALETTE[1], editable: true },
      ];
    }
    const s = this._hass.states;
    const ids = this._ids || [];
    return Object.keys(s)
      .filter((id) => id.startsWith('zone.'))
      .map((id, n) => {
        const a = s[id].attributes;
        const name = String(a.friendly_name || id);
        const home = id === 'zone.home';
        const match = ids.find((z) => z.name === name);
        return { entity: id, id: home ? 'home' : match && match.id, icon: (match && match.icon) || a.icon || 'mdi:map-marker', passive: !!(match && match.passive), name, lat: Number(a.latitude), lon: Number(a.longitude), r: Number(a.radius) || 100, home, colour: home ? HOME : PALETTE[n % PALETTE.length], editable: home || !!match };
      })
      .filter((z) => !isNaN(z.lat) && !isNaN(z.lon));
  }

  _fit() {
    const zs = this._zones();
    if (!zs.length) return;
    const b = L.latLngBounds(zs.map((z) => L.latLng(z.lat, z.lon).toBounds(z.r * 2)));
    this._map.fitBounds(b, { padding: [30, 30], maxZoom: 17 });
  }

  _draw(fit) {
    if (!this._map) return;
    this._layer.clearLayers();
    this._circles = {};
    this._zones().forEach((z) => {
      const circle = L.circle([z.lat, z.lon], { radius: z.r, color: z.colour, weight: 2, fillOpacity: 0.22 });
      circle.bindTooltip(`${z.home ? '🏠 ' : ''}${kitEsc(z.name)}`, { permanent: true, direction: 'center', className: 'zm-tag' });
      circle.on('click', (ev) => {
        L.DomEvent.stopPropagation(ev);
        this._select(z);
      });
      circle.addTo(this._layer);
      this._circles[z.entity] = circle;
    });
    if (fit) this._fit();
    if (this._sel) {
      // Keep editing the same zone after a redraw.
      const again = this._zones().find((z) => z.entity === this._sel.entity);
      if (again && !this._sel.dirty) this._select(again);
    }
  }

  async _search(q) {
    const box = this.querySelector('.zm-results');
    if (!q || q.trim().length < 3) {
      box.innerHTML = '';
      return;
    }
    try {
      const r = await fetch(`https://nominatim.openstreetmap.org/search?format=json&limit=5&countrycodes=gb&q=${encodeURIComponent(q)}`, { headers: { Accept: 'application/json' } });
      const list = await r.json();
      box.innerHTML = list.map((x) => `<button data-lat="${x.lat}" data-lon="${x.lon}">${kitEsc(x.display_name)}</button>`).join('') || '<button disabled>Nothing found</button>';
    } catch (err) {
      box.innerHTML = '<button disabled>Search isn’t available right now</button>';
    }
  }

  _mapClick(ev) {
    if (!this._adding || !this._admin()) return;
    this._adding = false;
    const add = this.querySelector('.zm-add');
    add.classList.remove('on');
    add.textContent = '+ Add zone';
    const z = { entity: null, id: null, name: '', lat: ev.latlng.lat, lon: ev.latlng.lng, r: 100, colour: PALETTE[0], editable: true, isNew: true };
    const circle = L.circle([z.lat, z.lon], { radius: z.r, color: z.colour, weight: 2, fillOpacity: 0.22, dashArray: '6 6' }).addTo(this._layer);
    this._circles.__new = circle;
    this._select(z);
  }

  // Edit one zone: a centre to drag (move) and a dot on the edge (size).
  _select(z) {
    this._clearHandles();
    this._sel = { ...z, dirty: !!z.isNew };
    const edit = this.querySelector('.zm-edit');
    if (!this._admin() || !z.editable) {
      edit.style.display = '';
      edit.innerHTML = `<b>${kitEsc(z.name)}</b><span class="zm-size">${Math.round(z.r)} m from the centre</span>${z.editable ? '' : '<span class="zm-size">(set in configuration.yaml)</span>'}`;
      return;
    }
    const circle = this._circles[z.entity || '__new'];
    const centre = L.marker([z.lat, z.lon], { draggable: true, icon: L.divIcon({ className: 'zm-mover' }), zIndexOffset: 1000 }).addTo(this._layer);
    const edgeAt = () => {
      const c = circle.getLatLng();
      return L.latLng(c.lat, c.lng + (circle.getRadius() / (111320 * Math.cos((c.lat * Math.PI) / 180))));
    };
    const handle = L.marker(edgeAt(), { draggable: true, icon: L.divIcon({ className: 'zm-handle' }), zIndexOffset: 1001 }).addTo(this._layer);
    centre.on('drag', () => {
      circle.setLatLng(centre.getLatLng());
      handle.setLatLng(edgeAt());
      this._changed();
    });
    handle.on('drag', () => {
      circle.setRadius(Math.max(15, Math.round(this._map.distance(circle.getLatLng(), handle.getLatLng()))));
      this._changed();
    });
    handle.on('dragend', () => handle.setLatLng(edgeAt()));
    this._handles = [centre, handle];
    this._circle = circle;
    edit.style.display = '';
    edit.innerHTML = `<input class="zm-name" value="${kitEsc(z.name)}" placeholder="Name (e.g. Frasers Group)" ${z.home ? 'disabled' : ''} aria-label="Zone name">
      <span class="zm-size"></span>
      ${z.home || z.isNew ? '' : '<button class="zm-btn del" data-act="delete">Delete</button>'}
      <button class="zm-btn" data-act="cancel">Cancel</button>
      <button class="zm-btn save" data-act="save">Save</button>`;
    edit.querySelector('.zm-name').addEventListener('input', () => (this._sel.dirty = true));
    this._changed(false);
    if (z.isNew) edit.querySelector('.zm-name').focus();
  }

  _changed(dirty = true) {
    if (!this._sel || !this._circle) return;
    if (dirty) this._sel.dirty = true;
    const r = Math.round(this._circle.getRadius());
    const size = this.querySelector('.zm-size');
    if (size) size.textContent = `${r} m from the centre`;
  }

  _clearHandles() {
    (this._handles || []).forEach((h) => this._layer.removeLayer(h));
    this._handles = null;
    this._circle = null;
  }

  _close(redraw = true) {
    this._clearHandles();
    this._sel = null;
    const edit = this.querySelector('.zm-edit');
    edit.style.display = 'none';
    edit.innerHTML = '';
    if (redraw) this._draw(false);
  }

  async _editClick(ev) {
    const b = ev.target.closest('[data-act]');
    if (!b || !this._sel) return;
    const z = this._sel;
    if (b.dataset.act === 'cancel') return this._close();
    const ws = (msg) => (this.config.demo ? Promise.resolve() : this._hass.callWS(msg));
    b.disabled = true;
    try {
      if (b.dataset.act === 'delete') {
        if (!confirm(`Delete the zone "${z.name}"? Anyone's places using it lose it too.`)) {
          b.disabled = false;
          return;
        }
        await ws({ type: 'zone/delete', zone_id: z.id });
      } else {
        const c = this._circle.getLatLng();
        const radius = Math.round(this._circle.getRadius());
        const name = (this.querySelector('.zm-name').value || '').trim();
        if (!z.home && !name) {
          this.querySelector('.zm-name').focus();
          b.disabled = false;
          return;
        }
        const lat = Number(c.lat.toFixed(6));
        const lon = Number(c.lng.toFixed(6));
        if (z.home) await ws({ type: 'config/core/update', latitude: lat, longitude: lon, radius });
        else if (z.isNew) await ws({ type: 'zone/create', name, latitude: lat, longitude: lon, radius, icon: 'mdi:map-marker', passive: false });
        else await ws({ type: 'zone/update', zone_id: z.id, name, latitude: lat, longitude: lon, radius, icon: z.icon || 'mdi:map-marker', passive: !!z.passive });
      }
      this._close(!this.config.demo);
    } catch (err) {
      b.disabled = false;
      const hint = this.querySelector('.zm-hint');
      hint.textContent = `Couldn't save: ${(err && err.message) || err}`;
    }
  }

  getCardSize() {
    return 8;
  }

  getGridOptions() {
    return { columns: 'full', rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`zone-map-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return {};
  }
}

export function registerZoneMapCard() {
  if (!customElements.get(`zone-map-card-editor${SUFFIX}`)) customElements.define(`zone-map-card-editor${SUFFIX}`, ZoneMapCardEditor);
  if (!customElements.get(`zone-map-card${SUFFIX}`)) customElements.define(`zone-map-card${SUFFIX}`, ZoneMapCard);
  window.customCards = window.customCards || [];
  window.customCards.push({
    type: `zone-map-card${SUFFIX}`,
    name: `Zone Map Card${LABEL}`,
    description: "Home Assistant's zones on a satellite map: search, add, move, resize, rename and delete",
    preview: true,
    documentationURL: 'https://github.com/J45PER/church-drive-cards#readme',
  });
}
