// Pure helpers for the camera card: signal and battery icons, whether the
// doorbell is ringing, the tile's shape, and the remembered view.

// Ring's Wi-Fi signal (ring-mqtt's `sensor.<x>_wireless`, in dBm; the Ring
// integration's `_wifi_signal_strength` / `_category`) as 0-4 bars.
export function signalBars(state, category = '') {
  const n = Number(state);
  if (state !== '' && state != null && isFinite(n) && n < 0) {
    if (n >= -55) return 4;
    if (n >= -65) return 3;
    if (n >= -75) return 2;
    return 1;
  }
  const c = String(category || state || '').toLowerCase();
  if (c === 'excellent') return 4;
  if (c === 'good') return 3;
  if (c === 'fair') return 2;
  if (c === 'poor' || c === 'bad') return 1;
  return -1; // unknown
}

export const SIGNAL_ICONS = ['mdi:wifi-strength-outline', 'mdi:wifi-strength-1', 'mdi:wifi-strength-2', 'mdi:wifi-strength-3', 'mdi:wifi-strength-4'];

export function signalIcon(bars) {
  return bars < 0 ? 'mdi:wifi-strength-off-outline' : SIGNAL_ICONS[bars];
}

// mdi:battery-10 ... mdi:battery (steps of 10).
export function batteryIcon(pct) {
  if (!isFinite(pct)) return 'mdi:battery-unknown';
  const p = Math.max(0, Math.min(100, Math.round(pct)));
  if (p >= 95) return 'mdi:battery';
  if (p < 5) return 'mdi:battery-outline';
  return `mdi:battery-${Math.floor(p / 10) * 10}`;
}

export function batteryColour(pct) {
  return !isFinite(pct) ? '#fff' : pct < 15 ? '#ef5350' : pct < 30 ? '#ffa726' : '#fff';
}

export function signalColour(bars) {
  return bars < 0 ? '#fff' : bars <= 1 ? '#ef5350' : bars === 2 ? '#ffa726' : '#fff';
}

// Ringing: the doorbell's binary sensor is on, or its ding event fired within
// `within` ms. `answered` (ms, when someone answered or dismissed) ends it.
export function ringing(dingBinary, dingEventState, now = Date.now(), answered = 0, within = 45000) {
  if (dingBinary === 'on') return !answered || now - answered > within ? true : false;
  const ms = Date.parse(dingEventState);
  if (!isFinite(ms)) return false;
  return now - ms < within && ms > answered;
}

// The tile's shape (width / height) from the picture's own, kept between 3:4
// and 16:9 so a tall doorbell still fits a row of cards. Unknown: 16:9.
export function tileAspect(w, h) {
  if (!w || !h) return 16 / 9;
  return Math.min(16 / 9, Math.max(3 / 4, w / h));
}

// The remembered view per camera: 'normal', 'expanded' (bigger, with the
// status icons) or 'full' is never remembered (it follows the phone turning).
const KEY = 'church-drive-camera-view';
export function loadView(entity, store = globalThis.localStorage) {
  try {
    const v = JSON.parse(store.getItem(KEY) || '{}')[entity];
    return v === 'expanded' ? 'expanded' : 'normal';
  } catch (_) {
    return 'normal';
  }
}
export function saveView(entity, view, store = globalThis.localStorage) {
  try {
    const all = JSON.parse(store.getItem(KEY) || '{}');
    all[entity] = view === 'expanded' ? 'expanded' : 'normal';
    store.setItem(KEY, JSON.stringify(all));
  } catch (_) {
    // No storage (private window): it just isn't remembered.
  }
}

// A camera's last known picture shape, so its tile is the right height before
// the picture has loaded (no jump when the page opens).
const AKEY = 'church-drive-camera-aspect';
export function loadAspect(entity, store = globalThis.localStorage) {
  try {
    const a = Number(JSON.parse(store.getItem(AKEY) || '{}')[entity]);
    return a > 0 ? a : 0;
  } catch (_) {
    return 0;
  }
}
export function saveAspect(entity, aspect, store = globalThis.localStorage) {
  try {
    const all = JSON.parse(store.getItem(AKEY) || '{}');
    all[entity] = Math.round(aspect * 1000) / 1000;
    store.setItem(AKEY, JSON.stringify(all));
  } catch (_) {
    // Not remembered.
  }
}
