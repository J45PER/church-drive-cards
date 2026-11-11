// Light and dark. The stylesheet follows the device's setting until someone presses the button at the top right; then their choice is kept in this browser
// (and put on the page as data-theme, which the stylesheet already honours). The button shows the icon of the mode it switches to: the moon while the page
// is light, the sun while it is dark. Both icons are the house's own (cd:moon, cd:sun).

import { fillIcons, icon } from './icons.js';

export const KEY = 'clive-manager-theme';

export const isTheme = (v) => v === 'dark' || v === 'light';
/** The theme in force: the one that was chosen, else the device's. */
export const effective = (stored, systemDark) => (isTheme(stored) ? stored : systemDark ? 'dark' : 'light');
export const opposite = (t) => (t === 'dark' ? 'light' : 'dark');

export function readStored(store = globalThis.localStorage) {
  try { const v = store.getItem(KEY); return isTheme(v) ? v : null; } catch { return null; }
}
export function writeStored(t, store = globalThis.localStorage) {
  try { store.setItem(KEY, t); } catch { /* a private window: the choice lasts for this visit only */ }
}

/** Puts a stored choice on the page before anything is drawn. */
export function initTheme(doc = globalThis.document, store = globalThis.localStorage) {
  const stored = readStored(store);
  if (stored) doc.documentElement.dataset.theme = stored;
  return stored;
}

/** Makes [btn] the light/dark button: the right icon and label now, and it switches the page and remembers the choice when pressed. */
export function mountThemeButton(btn, doc = globalThis.document, mql = globalThis.matchMedia('(prefers-color-scheme: dark)'), store = globalThis.localStorage) {
  const now = () => effective(readStored(store), mql.matches);
  const draw = () => {
    const next = opposite(now());
    btn.innerHTML = icon(next === 'dark' ? 'cd:moon' : 'cd:sun', 22);
    btn.setAttribute('aria-label', `Switch to ${next} mode`); btn.title = `Switch to ${next} mode`;
    fillIcons(btn);
  };
  btn.addEventListener('click', () => { const next = opposite(now()); doc.documentElement.dataset.theme = next; writeStored(next, store); draw(); });
  if (mql.addEventListener) mql.addEventListener('change', draw);
  draw();
}
