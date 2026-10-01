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
      const layer = MAP_LAYERS[style];
      this.__cdLabels = layer.labels;
      return init.call(this, layer.base, { ...(options || {}), attribution: ATTR, subdomains: 'abc', maxNativeZoom: 19, detectRetina: false, tileSize: 256, zoomOffset: 0 });
    }
    return init.call(this, url, options);
  };
  proto.onAdd = function (map) {
    const out = onAdd.call(this, map);
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
  proto.onRemove = function (map) {
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
}
