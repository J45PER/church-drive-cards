// Map style for every map in Home Assistant (the Zones page, map cards,
// person and device maps): satellite photos with road and place names on top,
// or a clearer street map, instead of Home Assistant's CartoDB map.
//
// Home Assistant draws maps with Leaflet, which registers itself as window.L
// when it loads. This catches that moment and changes Leaflet's tile layers:
// a CartoDB or OpenStreetMap layer becomes Esri's World Imagery (with Esri's
// place names and roads as a second layer) or Esri's World Street Map. If
// anything here fails, Home Assistant keeps its own map.
//
// With a Google Map Tiles key in Church Drive's options (Configure › Google
// maps), Google's maps are used instead: satellite with Google's roads, shop
// and place names on top, or Google's road map. Google's tiles need a session
// (made once, kept in this browser for about two weeks); until there is one,
// or if Google refuses the key, the Esri maps are shown.
//
// The style is kept per browser (localStorage `cd-map-style`): 'satellite'
// (the default), 'street', or 'ha' for Home Assistant's own. The zone map
// card's Satellite / Street buttons set it.

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
const GKEY = 'cd-gmap';
const GATTR = 'Map data &copy; Google';
const GTILES = 'https://tile.googleapis.com/v1';
const GSESSION = {
  satellite: { mapType: 'satellite', language: 'en-GB', region: 'GB', layerTypes: ['layerRoadmap'] },
  street: { mapType: 'roadmap', language: 'en-GB', region: 'GB' },
};
const REPLACE = /basemaps\.cartocdn\.com|tile\.openstreetmap\.org|tiles\.stadiamaps\.com/;

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

function gCache() {
  try {
    return JSON.parse(localStorage.getItem(GKEY) || 'null') || {};
  } catch (err) {
    return {};
  }
}

function gSave(cache) {
  try {
    localStorage.setItem(GKEY, JSON.stringify(cache));
  } catch (err) {
    /* storage blocked: a new session next time */
  }
}

let gMemory = null; // when localStorage is blocked

// Google's tiles for a style, if there's a key and a live session: { url, attribution }.
export function googleTiles(style) {
  const cache = gMemory || gCache();
  const s = cache.key && cache.sessions && cache.sessions[style];
  if (!s || Number(s.expiry) * 1000 < Date.now() + 3600e3) return null;
  return { url: `${GTILES}/2dtiles/{z}/{x}/{y}?session=${encodeURIComponent(s.session)}&key=${encodeURIComponent(cache.key)}`, attribution: GATTR };
}

let gReady = null;

// Ask Church Drive for the Google key and make the tile sessions (once a
// page). Resolves true when Google's maps can be used.
export function useGoogle(hass) {
  if (gReady) return gReady;
  if (!hass || !hass.callWS) return Promise.resolve(false);
  gReady = (async () => {
    let key = null;
    try {
      key = (await hass.callWS({ type: 'church_drive/maps' })).tiles_key;
    } catch (err) {
      return false; // older Church Drive
    }
    const cache = gCache();
    if (!key) {
      if (cache.key) gSave({});
      gMemory = {};
      return false;
    }
    if (cache.key !== key) Object.assign(cache, { key, sessions: {} });
    cache.sessions = cache.sessions || {};
    for (const style of Object.keys(GSESSION)) {
      const s = cache.sessions[style];
      if (s && Number(s.expiry) * 1000 > Date.now() + 86400e3) continue;
      try {
        const r = await fetch(`${GTILES}/createSession?key=${encodeURIComponent(key)}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(GSESSION[style]) });
        const j = await r.json();
        if (!r.ok || !j.session) throw new Error((j.error && j.error.message) || r.status);
        cache.sessions[style] = { session: j.session, expiry: j.expiry };
      } catch (err) {
        console.warn('Church Drive maps: Google refused the Map Tiles key, so Esri maps are shown.', err && err.message);
        delete cache.sessions[style];
      }
    }
    gSave(cache);
    gMemory = cache;
    const ok = !!googleTiles('satellite') || !!googleTiles('street');
    if (ok) window.dispatchEvent(new CustomEvent('cd-map-google'));
    return ok;
  })();
  return gReady;
}

// Change a Leaflet's tile layers (once per Leaflet).
function patch(L) {
  if (!L || !L.TileLayer || L.TileLayer.prototype.__cdPatched) return;
  const proto = L.TileLayer.prototype;
  proto.__cdPatched = true;
  const init = proto.initialize;
  const onAdd = proto.onAdd;
  const onRemove = proto.onRemove;
  proto.initialize = function (url, options) {
    const style = mapStyle();
    if (style !== 'ha' && typeof url === 'string' && REPLACE.test(url)) {
      const g = googleTiles(style);
      if (g) return init.call(this, g.url, { ...(options || {}), attribution: g.attribution, subdomains: 'abc', maxNativeZoom: 20, detectRetina: false, tileSize: 256, zoomOffset: 0 });
      // Esri for now; Google once its session is ready (see onAdd).
      this.__cdWait = style;
      const layer = MAP_LAYERS[style];
      this.__cdLabels = layer.labels;
      return init.call(this, layer.base, { ...(options || {}), attribution: ATTR, subdomains: 'abc', maxNativeZoom: 19, detectRetina: false, tileSize: 256, zoomOffset: 0 });
    }
    return init.call(this, url, options);
  };
  proto.onAdd = function (map) {
    const out = onAdd.call(this, map);
    if (this.__cdWait && !this.__cdListen) {
      this.__cdListen = () => this.__cdToGoogle(map);
      window.addEventListener('cd-map-google', this.__cdListen);
    }
    try {
      if (this.__cdLabels && this.__cdLabels.length && !this.__cdOver) {
        this.__cdOver = this.__cdLabels.map((u) => {
          const over = new L.TileLayer(u, { maxNativeZoom: 19, maxZoom: this.options.maxZoom, zIndex: 5 });
          over.__cdOverlay = true;
          return over;
        });
      }
      (this.__cdOver || []).forEach((o) => !map.hasLayer(o) && o.addTo(map));
    } catch (err) {
      /* labels are a nice-to-have */
    }
    return out;
  };
  // Swap an Esri layer for Google's once a session is ready.
  proto.__cdToGoogle = function (map) {
    const g = googleTiles(this.__cdWait);
    if (!g || !this._map) return;
    this.__cdWait = null;
    window.removeEventListener('cd-map-google', this.__cdListen);
    (this.__cdOver || []).forEach((o) => map.hasLayer(o) && map.removeLayer(o));
    this.__cdOver = [];
    this.__cdLabels = [];
    if (map.attributionControl) {
      map.attributionControl.removeAttribution(this.options.attribution);
      map.attributionControl.addAttribution(g.attribution);
    }
    this.options.attribution = g.attribution;
    this.options.maxNativeZoom = 20;
    this.setUrl(g.url);
  };
  proto.onRemove = function (map) {
    if (this.__cdListen) window.removeEventListener('cd-map-google', this.__cdListen);
    this.__cdListen = null;
    (this.__cdOver || []).forEach((o) => map.hasLayer(o) && map.removeLayer(o));
    return onRemove.call(this, map);
  };
}

// Watch for Leaflet arriving (Home Assistant loads it when a map is first
// shown). Runs once per page, even with the beta bundle loaded too.
export function installMapStyle() {
  if (window.__cdMapStyle) return;
  window.__cdMapStyle = true;
  try {
    let current = window.L;
    if (current) patch(current);
    Object.defineProperty(window, 'L', {
      configurable: true,
      enumerable: true,
      get: () => current,
      set: (v) => {
        current = v;
        try {
          patch(v);
        } catch (err) {
          /* leave Home Assistant's map as it is */
        }
      },
    });
  } catch (err) {
    /* couldn't watch: Home Assistant keeps its own map */
  }
  // Get the Google key once Home Assistant's connection is up.
  let tries = 0;
  const look = () => {
    const ha = document.querySelector('home-assistant');
    const hass = ha && ha.hass;
    if (hass && hass.connected !== false && hass.callWS) useGoogle(hass);
    else if (++tries < 60) setTimeout(look, 1000);
  };
  setTimeout(look, 1000);
}
