// Puts Clive's colours under every dashboard: Home Assistant's own colour variables (the status colours, the named colours a card's
// `color: amber` picks, the accent) are set from the house palette (design/clive/cards.json through clive-colours.js), so a card
// that asks HA for "green" or "orange" gets the house's own, not Material's. Backgrounds and text stay with the dashboard's theme.
import { CLIVE } from './clive-colours.js';

const NAMED = {
  red: 'bad', pink: 'media', purple: 'doors', 'deep-purple': 'alarm', indigo: 'tasks', blue: 'alarm_home', 'light-blue': 'cooling',
  cyan: 'air', teal: 'air', green: 'ok', 'light-green': 'plugs', lime: 'plugs', yellow: 'lights', amber: 'warn', orange: 'warn',
  'deep-orange': 'heating', brown: 'blinds', 'blue-grey': 'rain',
};
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
  for (const [name, key] of Object.entries(NAMED)) v[`${name}-color`] = hex(key);
  // A card's `color` may also be a palette name (lights, gregg, alarm_home…): `var(--lights-color)` is the house's own.
  for (const key of Object.keys(CLIVE)) v[`${key}-color`] = hex(key);
  const lines = Object.entries(v).map(([k, c]) => `  --${k}: ${c};` + (/^(primary|accent)-color$/.test(k) ? ` --rgb-${k}: ${rgb(c)};` : ''));
  return `:root, html, body {\n${lines.join('\n')}\n}`;
}

/** Adds the colours to the page once; a theme chosen later in HA still wins over nothing but these variables, so call again if it changes. */
export function installCliveTheme() {
  if (typeof document === 'undefined' || document.getElementById('cd-clive-colours')) return;
  const style = document.createElement('style');
  style.id = 'cd-clive-colours';
  style.textContent = cliveThemeCss();
  document.head.appendChild(style);
}
