// Look of a scene tile: a picture, or a gradient in the scene's palette, plus
// an icon. Looks are keyed by scene *name* (e.g. "Cool bright"), so one style
// applies to that scene in every room.
//
// Precedence: a light card's own per-scene override > the central styles set
// on the Design Presets "Scene styles" tab (scene-styles-card) > the built-in
// defaults below. Hue doesn't expose scene artwork or colours to Home
// Assistant, so the built-in defaults approximate the Hue scenes by name;
// anything unknown gets a stable colour pair derived from its name.

import { SUFFIX } from './suffix.js';

const PALETTES = {
  bright: ['#fff6e0', '#ffd98a'],
  'cool bright': ['#f4f9ff', '#bcd8ff'],
  dimmed: ['#8a6630', '#3b2a14'],
  nightlight: ['#ff8a2a', '#3a1a05'],
  relax: ['#ffb35c', '#e0702a'],
  rest: ['#ff9f4a', '#8f4416'],
  read: ['#fff1d6', '#ffc978'],
  concentrate: ['#f2f6ff', '#a9c7ff'],
  energise: ['#d9ecff', '#5d9eff'],
  'natural light': ['#ffe0a0', '#9fd0ff'],
  soho: ['#ff4f8b', '#ffb347', '#7b2ff7'],
  'lake placid': ['#0f5e9c', '#35baf6', '#9fe2bf'],
  'toil and trouble': ['#6a0dad', '#2e8b57', '#ff7f00'],
  'bright & blue': ['#e8f4ff', '#3d7dff'],
  arise: ['#ff7b39', '#ffd27f'],
  spellbound: ['#3a0ca3', '#f72585', '#4cc9f0'],
  storybook: ['#ffadad', '#ffd6a5', '#9bf6ff'],
  unwind: ['#ff9966', '#ff5e62'],
  'pumpkin patch': ['#ff7518', '#8b4513', '#ffb347'],
  shine: ['#fffbd6', '#ffd23f'],
  phantom: ['#2d0a4e', '#6c2bd9', '#0f0f2e'],
  'city blue': ['#0b1d51', '#2f6fd6', '#89c2ff'],
  aqua: ['#00c9d6', '#0077b6', '#90e0ef'],
  'dreamy dusk': ['#6a4c93', '#f28482', '#ffb4a2'],
  'emerald isle': ['#1b7f6b', '#52b788', '#b7e4c7'],
  magneto: ['#3a0ca3', '#4361ee', '#f72585'],
  meriete: ['#ff9e7a', '#c86b98', '#5f4b8b'],
  motown: ['#7b2cbf', '#ff6d00', '#ffd60a'],
  'ruby glow': ['#9b111e', '#e0115f', '#ff6f61'],
  'witching hour': ['#240046', '#5a189a', '#ff7900'],
};

const ICONS = [
  [/night/, 'mdi:weather-night'],
  [/read/, 'mdi:book-open-variant'],
  [/concentrat/, 'mdi:head-lightbulb-outline'],
  [/energi/, 'mdi:lightning-bolt'],
  [/relax|unwind/, 'mdi:sofa-outline'],
  [/rest/, 'mdi:bed-outline'],
  [/dim/, 'mdi:brightness-5'],
  [/bright|shine|arise/, 'mdi:white-balance-sunny'],
  [/natural/, 'mdi:weather-sunset'],
];

export function sceneKey(name) {
  // "Natural light 2" -> "natural light"
  return String(name || '').toLowerCase().replace(/\s+\d+$/, '').trim();
}

export function builtInSceneNames() {
  return Object.keys(PALETTES);
}

function toHex(c) {
  if (Array.isArray(c)) return '#' + c.map((v) => Math.max(0, Math.min(255, v | 0)).toString(16).padStart(2, '0')).join('');
  return c;
}

function hslHex(h, s, l) {
  const a = s * Math.min(l, 1 - l);
  const f = (n) => {
    const k = (n + h / 30) % 12;
    return Math.round(255 * (l - a * Math.max(-1, Math.min(k - 3, 9 - k, 1))));
  };
  return toHex([f(0), f(8), f(4)]);
}

// ---- central styles -------------------------------------------------------

let central = {};
const STYLES_EVENT = 'church-drive-scene-styles';

// Styles from the scene-styles-card config: [{ scene, icon, colour_1..3, image }].
export function setSceneStyles(list) {
  const next = {};
  (list || []).forEach((s) => {
    if (s && s.scene) next[sceneKey(s.scene)] = s;
  });
  if (JSON.stringify(next) === JSON.stringify(central)) return;
  central = next;
  window.dispatchEvent(new CustomEvent(STYLES_EVENT));
}

export function centralSceneStyle(name) {
  return central[sceneKey(name)] || null;
}

export function onSceneStylesChanged(callback) {
  window.addEventListener(STYLES_EVENT, callback);
  return () => window.removeEventListener(STYLES_EVENT, callback);
}

// Light cards on any dashboard read the central styles from the Design
// Presets dashboard config, once per page load. The beta build prefers a
// scene-styles-card-beta (so style changes can be tried on the Beta tab)
// and falls back to the released card.
const STYLES_DASHBOARD = 'design-presets';
let loading = null;

function findStyleCards(node, found) {
  if (Array.isArray(node)) node.forEach((n) => findStyleCards(n, found));
  else if (node && typeof node === 'object') {
    if (typeof node.type === 'string' && /^custom:scene-styles-card(-beta)?$/.test(node.type)) found.push(node);
    Object.values(node).forEach((v) => findStyleCards(v, found));
  }
  return found;
}

export function loadSceneStyles(hass) {
  if (loading || !hass || !hass.callWS) return loading;
  loading = hass
    .callWS({ type: 'lovelace/config', url_path: STYLES_DASHBOARD })
    .then((config) => {
      const cards = findStyleCards(config, []);
      const mine = cards.find((c) => c.type === `custom:scene-styles-card${SUFFIX}`) || cards[0];
      if (mine) setSceneStyles(mine.styles);
    })
    .catch(() => {
      // No access or no dashboard: built-in defaults still apply.
    });
  return loading;
}

// ---- looks ----------------------------------------------------------------

// Hex colours for the scene: central colours, built-in palette, or a stable
// pair derived from the name.
// `fallback` is a universal scene's real colours (from the library, read
// from Hue); they win over the built-in approximations below.
export function scenePalette(name, fallback) {
  const style = centralSceneStyle(name);
  const custom = style ? [style.colour_1, style.colour_2, style.colour_3].filter(Boolean).map(toHex) : [];
  if (custom.length) return custom.length === 1 ? [custom[0], custom[0]] : custom;
  if (fallback && fallback.length) return fallback.length === 1 ? [fallback[0], fallback[0]] : fallback;
  const key = sceneKey(name);
  if (PALETTES[key]) return PALETTES[key];
  let h = 0;
  for (const ch of key) h = (h * 31 + ch.charCodeAt(0)) % 360;
  return [hslHex(h, 0.7, 0.55), hslHex((h + 50) % 360, 0.7, 0.35)];
}

// CSS background: an explicit picture (card override or central), else the
// palette gradient.
export function sceneBackground(name, image, fallback) {
  const style = centralSceneStyle(name);
  const picture = image || (style && style.image);
  if (picture) return `center / cover no-repeat url("${picture}")`;
  return `linear-gradient(135deg, ${scenePalette(name, fallback).join(', ')})`;
}

export function sceneIcon(name, isDynamic) {
  const style = centralSceneStyle(name);
  if (style && style.icon) return style.icon;
  const key = sceneKey(name);
  for (const [re, icon] of ICONS) if (re.test(key)) return icon;
  return isDynamic ? 'mdi:animation-play-outline' : 'mdi:palette-outline';
}
