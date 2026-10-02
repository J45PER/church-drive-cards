// Map style for every map in Home Assistant (the Zones page, map cards,
// person and device maps): satellite photos with road and place names on top,
// or a street map, instead of Home Assistant's own.
//
// With a Google Map Tiles key in Church Drive's options (Configure › Google
// maps), the maps are Google's: satellite with Google's roads, shop and place
// names on top, or Google's road map. Home Assistant fetches them from Google
// (maps.py; the key is locked to Home Assistant's addresses, which browsers
// don't send) and serves them at church_drive/maps' `tile_url`. Without a key,
// Esri's World Imagery (with Esri's roads and place names) or World Street Map.
//
// Home Assistant's maps (<ha-map>): in 2026.9 a Leaflet map whose base is a
// MapLibre layer (or raster tiles without WebGL2); in newer versions a map
// engine (MapLibre, or Leaflet as a fallback). Each ha-map is watched for its
// map; once it has one, the base map is swapped and ours goes underneath
// everything else (zones, people). If anything here fails, Home
// Assistant keeps its own map.
//
// The style is kept per browser (localStorage `cd-map-style`): 'satellite'
// (the default), 'street', or 'ha' for Home Assistant's own. The zone map
// card's Satellite / Street buttons set it.

import { SUFFIX } from './suffix.js';

const ESRI = 'https://server.arcgisonline.com/ArcGIS/rest/services';
const ATTR = 'Tiles &copy; Esri &mdash; Source: Esri, Maxar, Earthstar Geographics, and the GIS User Community';
export const MAP_LAYERS = {
  satellite: {
    base: `${ESRI}/World_Imagery/MapServer/tile/{z}/{y}/{x}`,
    labels: [`${ESRI}/Reference/World_Transportation/MapServer/tile/{z}/{y}/{x}`, `${ESRI}/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}`],
  },
  street: { base: `${ESRI}/World_Street_Map/MapServer/tile/{z}/{y}/{x}`, labels: [] },
};
const KEY = 'cd-map-style';
const GATTR = 'Map data &copy; Google';
const RASTER = 'cd-raster-';

export function mapStyle() {
  try {
    const v = localStorage.getItem(KEY);
    return v === 'street' || v === 'ha' ? v : 'satellite';
  } catch (err) {
    return 'satellite';
  }
}

export function setMapStyle(style) {
  try {
    localStorage.setItem(KEY, style);
  } catch (err) {
    /* storage blocked: this page only */
  }
  window.dispatchEvent(new CustomEvent('cd-map-style', { detail: style }));
}

let gUrl = null; // Home Assistant's tile address for Google, with {style}
let gReady = null;

// Google's tiles for a style (through Home Assistant), if there's a key: { url, attribution }.
export function googleTiles(style) {
  if (!gUrl) return null;
  return { url: new URL(gUrl.replace('{style}', style), location.href).href.replace(/%7B/g, '{').replace(/%7D/g, '}'), attribution: GATTR };
}

// Ask Church Drive where Google's tiles are (once a page). Resolves true when
// Google's maps can be used.
export function useGoogle(hass) {
  if (gReady) return gReady;
  if (!hass || !hass.callWS) return Promise.resolve(false);
  gReady = hass.callWS({ type: 'church_drive/maps' }).then(
    (r) => {
      gUrl = (r && r.tile_url) || null;
      if (gUrl) window.dispatchEvent(new CustomEvent('cd-map-google'));
      return !!gUrl;
    },
    () => false, // older Church Drive
  );
  return gReady;
}

// The layers to draw for the chosen style: Google's, or Esri's (base, then labels).
function layersFor(style) {
  const g = googleTiles(style);
  if (g) return [{ url: g.url, attribution: g.attribution, maxzoom: 20 }];
  const e = MAP_LAYERS[style];
  return [e.base, ...e.labels].map((url, i) => ({ url, attribution: i ? '' : ATTR, maxzoom: 19 }));
}

// MapLibre: hide the style's own map (its vector and background layers) and
// put the raster layers underneath whatever else is drawn (zones, people).
function applyMaplibre(m) {
  const st = m.getStyle && m.getStyle();
  if (!st || !st.layers) return;
  st.layers.filter((l) => l.id.startsWith(RASTER)).forEach((l) => m.removeLayer(l.id));
  Object.keys(st.sources || {}).filter((s) => s.startsWith(RASTER)).forEach((s) => m.removeSource(s));
  const style = mapStyle();
  const own = st.layers.filter((l) => !l.id.startsWith(RASTER));
  const isBase = (l) => l.type === 'background' || (l.source && st.sources[l.source] && st.sources[l.source].type === 'vector');
  own.filter(isBase).forEach((l) => m.setLayoutProperty(l.id, 'visibility', style === 'ha' ? 'visible' : 'none'));
  if (style === 'ha') return;
  const before = own.length ? own[0].id : undefined;
  layersFor(style).forEach((spec, i) => {
    const id = `${RASTER}${i}`;
    m.addSource(id, { type: 'raster', tiles: [spec.url], tileSize: 256, maxzoom: spec.maxzoom, attribution: spec.attribution || undefined });
    m.addLayer({ id, type: 'raster', source: id }, before);
  });
}

function hookMaplibre(m) {
  if (m.__cdHooked) return m.__cdApply();
  m.__cdHooked = true;
  m.__cdApply = () => {
    try {
      if (m.isStyleLoaded()) applyMaplibre(m);
      else m.once('idle', () => m.__cdApply());
    } catch (err) {
      /* leave Home Assistant's map as it is */
    }
  };
  // A new style (Home Assistant swaps it for dark mode) drops the raster layers.
  m.on('styledata', () => {
    const st = m.getStyle && m.getStyle();
    const want = mapStyle() !== 'ha';
    const have = !!(st && st.sources && st.sources[`${RASTER}0`]);
    if (want !== have) m.__cdApply();
  });
  m.__cdApply();
}

// Leaflet: point the base tile layer at the new tiles (labels as extra layers).
function applyLeaflet(m) {
  const style = mapStyle();
  m.eachLayer((l) => {
    if (l.__cdOverlay) m.removeLayer(l);
  });
  m.eachLayer((l) => {
    if (!l.setUrl || !l._url || l.__cdOverlay) return;
    if (!l.__cdOrig) l.__cdOrig = { url: l._url, max: l.options.maxNativeZoom };
    if (style === 'ha') {
      l.options.maxNativeZoom = l.__cdOrig.max;
      l.setUrl(l.__cdOrig.url);
      return;
    }
    const [base, ...labels] = layersFor(style);
    l.options.maxNativeZoom = base.maxzoom;
    l.setUrl(base.url);
    labels.forEach((spec) => {
      const over = new l.constructor(spec.url, { maxNativeZoom: spec.maxzoom, maxZoom: l.options.maxZoom, zIndex: 5 });
      over.__cdOverlay = true;
      over.addTo(m);
    });
  });
}

// Home Assistant 2026.9's ha-map: a Leaflet map (el.leafletMap, el.Leaflet)
// whose base layer is a MapLibre layer (maplibre-gl-leaflet), or a raster tile
// layer without WebGL2. That layer is hidden and ours go under everything else;
// Home Assistant inverts tiles in dark mode (--map-filter), which ours undo.
function applyHaLeaflet(el) {
  const m = el.leafletMap;
  const Lf = el.Leaflet;
  if (!m || !Lf || !Lf.tileLayer) return false;
  (m.__cdLayers || []).forEach((l) => m.removeLayer(l));
  m.__cdLayers = [];
  const style = mapStyle();
  m.eachLayer((l) => {
    if (l.__cdOurs) return;
    if (typeof l.getMaplibreMap === 'function') {
      const c = l.getContainer && l.getContainer();
      if (c) c.style.visibility = style === 'ha' ? '' : 'hidden';
    } else if (l._url && typeof l.setOpacity === 'function') {
      l.setOpacity(style === 'ha' ? 1 : 0);
    }
  });
  addSwitch(m, Lf);
  if (style === 'ha') return true;
  layersFor(style).forEach((spec, i) => {
    const t = Lf.tileLayer(spec.url, { maxNativeZoom: spec.maxzoom, maxZoom: 20, attribution: spec.attribution || undefined, zIndex: 1 + i });
    t.__cdOurs = true;
    t.on('tileloadstart tileload', (e) => {
      e.tile.style.filter = 'none';
    });
    t.addTo(m);
    m.__cdLayers.push(t);
  });
  return true;
}

// A Satellite / Street switch in the map's top right corner (Leaflet maps).
function addSwitch(m, Lf) {
  if (m.__cdSwitch || !Lf.Control) return;
  const Switch = Lf.Control.extend({
    onAdd() {
      const box = document.createElement('div');
      box.className = 'leaflet-bar';
      box.style.cssText = 'display:flex; overflow:hidden; border-radius:10px; border:none; box-shadow:0 1px 4px rgba(0,0,0,0.4);';
      const paint = () => {
        const now = mapStyle() === 'street' ? 'street' : 'satellite';
        box.querySelectorAll('button').forEach((b) => {
          const on = b.dataset.style === now;
          b.style.background = on ? '#26a69a' : 'rgba(31,33,40,0.92)';
          b.style.color = '#fff';
        });
      };
      [['satellite', 'Satellite'], ['street', 'Street']].forEach(([style, label]) => {
        const b = document.createElement('button');
        b.type = 'button';
        b.dataset.style = style;
        b.textContent = label;
        b.style.cssText = 'border:none; cursor:pointer; font:700 12px/1 sans-serif; padding:8px 10px;';
        b.addEventListener('click', (ev) => {
          ev.stopPropagation();
          setMapStyle(style);
        });
        box.appendChild(b);
      });
      Lf.DomEvent.disableClickPropagation(box);
      paint();
      this._paint = paint;
      window.addEventListener('cd-map-style', paint);
      return box;
    },
    onRemove() {
      window.removeEventListener('cd-map-style', this._paint);
    },
  });
  m.__cdSwitch = new Switch({ position: 'topright' }).addTo(m);
}

// Whichever map an ha-map has: a newer Home Assistant's engine (MapLibre or
// Leaflet), or 2026.9's Leaflet map.
function mapKey(el) {
  return el._engine || el.leafletMap || null;
}

function applyMap(el) {
  try {
    if (el._engine) return applyEngine(el._engine);
    if (el.leafletMap) return applyHaLeaflet(el);
  } catch (err) {
    /* leave Home Assistant's map as it is */
  }
  return false;
}

function applyEngine(engine) {
  const m = engine && engine._map;
  if (!m) return false;
  if (typeof m.addSource === 'function') hookMaplibre(m);
  else if (typeof m.eachLayer === 'function') applyLeaflet(m);
  else return false;
  return true;
}

const maps = new Set();

function reapplyAll() {
  maps.forEach((el) => {
    if (!el.isConnected || !mapKey(el)) maps.delete(el);
    else applyMap(el);
  });
}

// Watch each <ha-map> for its map (it sets one up after connecting, and again
// after a fallback or rebuild).
function hookHaMap(Cls) {
  const proto = Cls.prototype;
  if (proto.__cdHooked3) return;
  proto.__cdHooked3 = true;
  // Not connectedCallback: the browser keeps the one the element was defined
  // with. Lit's updated() is looked up each time, so a map is caught on its
  // first render.
  const updated = proto.updated;
  proto.updated = function (...args) {
    if (!this.__cdWatch) watch(this);
    return updated && updated.apply(this, args);
  };
  // Maps already on the page before this ran.
  findAll(document, 'ha-map').forEach(watch);
}

// Every <tag> on the page, inside shadow roots too.
function findAll(root, tag, out = []) {
  root.querySelectorAll('*').forEach((el) => {
    if (el.localName === tag) out.push(el);
    if (el.shadowRoot) findAll(el.shadowRoot, tag, out);
  });
  return out;
}

// Check a map for a new engine: often at first, then every few seconds (an
// engine can be rebuilt later, e.g. falling back to Leaflet).
function watch(el) {
  clearInterval(el.__cdWatch);
  let ticks = 0;
  const tick = () => {
    if (!el.isConnected) {
      // Gone (a closed dialog); updated() starts watching again if it's reused.
      clearInterval(el.__cdWatch);
      el.__cdWatch = null;
      el.__cdKey = null;
      maps.delete(el);
      return;
    }
    const key = mapKey(el);
    if (key && key !== el.__cdKey && applyMap(el)) {
      el.__cdKey = key;
      maps.add(el);
    }
    if (++ticks === 60) {
      clearInterval(el.__cdWatch);
      el.__cdWatch = setInterval(tick, 3000);
    }
  };
  el.__cdWatch = setInterval(tick, 250);
}

// Once per page, even with the beta bundle loaded too.
export function installMapStyle() {
  if (window.__cdMapStyle3) return;
  window.__cdMapStyle3 = true;
  try {
    if (window.customElements) customElements.whenDefined('ha-map').then(hookHaMap);
  } catch (err) {
    /* Home Assistant keeps its own maps */
  }
  window.addEventListener('cd-map-google', reapplyAll);
  window.addEventListener('cd-map-style', reapplyAll);
  // Ask where Google's tiles are once Home Assistant's connection is up.
  let tries = 0;
  const look = () => {
    const ha = document.querySelector('home-assistant');
    const hass = ha && ha.hass;
    if (hass && hass.connected !== false && hass.callWS) useGoogle(hass);
    else if (++tries < 60) setTimeout(look, 1000);
  };
  setTimeout(look, 1000);
}
