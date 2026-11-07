// Puts Clive's colours under every dashboard: Home Assistant's own colour variables (the status colours, the named colours a card's
// `color: amber` picks, the accent) are set from the house palette (design/clive/cards.json through clive-colours.js), so a card
// that asks HA for "green" or "orange" gets the house's own, not Material's. Backgrounds and text stay with the dashboard's theme.
import { CLIVE, CLIVE_ALIASES } from './clive-colours.js';

const hex = (key, slot = 0) => CLIVE[key][slot];
const rgb = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16)).join(', ');

export function cliveThemeCss() {
  const v = {
    'primary-color': hex('alarm'),
    'accent-color': hex('alarm'),
    'success-color': hex('ok'),
    'warning-color': hex('warn'),
    'error-color': hex('bad'),
    'info-color': hex('alarm_home'),
    'state-active-color': hex('alarm'),
    'state-error-color': hex('bad'),
    'state-warning-color': hex('warn'),
    'state-success-color': hex('ok'),
    'state-light-active-color': hex('lights'),
    'state-climate-heat-color': hex('heating'),
    'state-climate-cool-color': hex('cooling'),
    'state-climate-auto-color': hex('ok'),
    'state-alarm_armed_home-color': hex('alarm_home'),
    'state-alarm_armed_away-color': hex('alarm_away'),
    'state-alarm_disarmed-color': hex('alarm_off'),
    'state-alarm_arming-color': hex('arming_home'),
    'state-alarm_pending-color': hex('arming_away'),
    'state-alarm_triggered-color': hex('bad'),
  };
  for (const [name, key] of Object.entries(CLIVE_ALIASES)) v[`${name}-color`] = hex(key);
  // A card's `color` may also be a palette name (lights, gregg, alarm_home…): `var(--lights-color)` is the house's own.
  for (const key of Object.keys(CLIVE)) v[`${key}-color`] = hex(key);
  const lines = Object.entries(v).map(([k, c]) => `  --${k}: ${c};` + (/^(primary|accent)-color$/.test(k) ? ` --rgb-${k}: ${rgb(c)};` : ''));
  return `:root, html, body {\n${lines.join('\n')}\n}`;
}

/** Palettes from Home Assistant ({key: {colours: [five hex]}}) replace the shipped ones, and a palette added there gets its variable too. */
export function applyLive(palettes) {
  if (!palettes || typeof palettes !== 'object') return;
  for (const [key, p] of Object.entries(palettes)) {
    if (/^[a-z][a-z0-9_]{0,39}$/.test(key) && p && Array.isArray(p.colours) && p.colours.length === 5 && p.colours.every((c) => /^#[0-9a-f]{6}$/i.test(c))) {
      CLIVE[key] = p.colours.map((c) => c.toLowerCase());
    }
  }
}

const CACHE_KEY = 'cd-clive-palettes';

function paint() {
  if (typeof document === 'undefined') return;
  let style = document.getElementById('cd-clive-colours');
  if (!style) {
    style = document.createElement('style');
    style.id = 'cd-clive-colours';
    document.head.appendChild(style);
  }
  style.textContent = cliveThemeCss();
}

/** Adds the colours to the page, then asks Home Assistant for the live palettes and follows its changes (no release needed for a colour). */
export function installCliveTheme() {
  if (typeof document === 'undefined' || document.getElementById('cd-clive-colours')) return;
  try {
    applyLive(JSON.parse(localStorage.getItem(CACHE_KEY) || 'null'));
  } catch (err) {
    /* storage blocked or bad data: the shipped colours */
  }
  paint();
  let tries = 0;
  const find = setInterval(() => {
    const ha = document.querySelector('home-assistant');
    const hass = ha && ha.hass;
    if (!hass || !hass.callWS) {
      if (++tries > 90) clearInterval(find);
      return;
    }
    clearInterval(find);
    const load = () => hass.callWS({ type: 'church_drive/colours' }).then((r) => {
      if (!r || !r.palettes) return;
      applyLive(r.palettes);
      paint();
      try { localStorage.setItem(CACHE_KEY, JSON.stringify(r.palettes)); } catch (err) { /* storage blocked */ }
    }).catch(() => {});
    load();
    if (hass.connection && hass.connection.subscribeEvents) hass.connection.subscribeEvents(load, 'church_drive_colours_changed').catch(() => {});
  }, 1000);
}
