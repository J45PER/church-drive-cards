// The mode icons, kept once in the Church Drive integration (icons.py) and read
// over the websocket, so the fan's Sleep, the thermostat's Eco or the charger's
// Stop look the same on every card and in the Android app, and change in one
// place (the Icon Styles card). Cards ask `iconFor(group, key, fallback)`; the
// fallback is the icon the card always had, used until the list arrives and if
// Church Drive isn't running.

const EVENT = 'church-drive-icons';
const RETRY_MS = 15000;

let icons = {};
let defaults = {};
let groups = {};
let loading = null;
let lastTry = 0;
let watching = false;

export function loadIcons(hass, force = false) {
  if (!hass || !hass.callWS) return loading;
  if (force) loading = null;
  if (loading) return loading;
  if (Date.now() - lastTry < RETRY_MS && !force) return null;
  lastTry = Date.now();
  loading = hass
    .callWS({ type: 'church_drive/icons' })
    .then((res) => {
      icons = (res && res.icons) || {};
      defaults = (res && res.defaults) || {};
      groups = (res && res.groups) || {};
      window.dispatchEvent(new CustomEvent(EVENT));
      // Someone changed an icon: fetch the list again.
      if (!watching && hass.connection && hass.connection.subscribeEvents) {
        watching = true;
        hass.connection.subscribeEvents(() => loadIcons(hass, true), 'church_drive_icons_changed').catch(() => (watching = false));
      }
    })
    .catch(() => {
      // Integration not installed, or Home Assistant still starting: the built-in icons stay, and the next try is in a while.
      loading = null;
    });
  return loading;
}

// The icon for a mode: Home Assistant's choice, else `fallback`.
export function iconFor(group, key, fallback) {
  const g = icons[group];
  const icon = g && g[String(key == null ? '' : key).toLowerCase()];
  return icon || fallback;
}

// "speed_low" as "Speed low", "eco+" as "Eco+".
export function iconLabel(key) {
  const t = String(key).replace(/_/g, ' ');
  return t.charAt(0).toUpperCase() + t.slice(1);
}

// For the Icon Styles card: every slot, its built-in icon and its group's name.
export function iconSlots() {
  return { icons, defaults, groups };
}

export function onIconsChanged(callback) {
  window.addEventListener(EVENT, callback);
  return () => window.removeEventListener(EVENT, callback);
}

// Test hook: forget what was loaded.
export function resetIconsForTest() {
  icons = {};
  defaults = {};
  groups = {};
  loading = null;
  lastTry = 0;
  watching = false;
}

// Cards call this from `set hass`: it loads the list (once) and redraws the
// card when the icons arrive or change. Holds the card weakly, so a card that
// has left the page doesn't linger.
export function watchIcons(card, hass) {
  loadIcons(hass);
  if (card._iconWatch) return;
  const ref = new WeakRef(card);
  const off = onIconsChanged(() => {
    const c = ref.deref();
    if (!c || !c.isConnected) return off();
    c._sig = null;
    if (c._render) c._render();
  });
  card._iconWatch = off;
}
