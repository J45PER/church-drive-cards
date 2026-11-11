// The house style on the website: the apps' section panels (an icon, a coloured title and a summary pill) and tone cards in the palette colours,
// drawn from the live palettes so a palette change shows here too. Used by the pages that follow the house's own dashboard (Energy, Devices).
import { h } from './pages/dom.js';
import { icon } from './icons.js';
import { OWN_LOGOS } from './pages/own-logos.js';

const NEUTRAL = ['#9AA4B2', '#202733', '#9AA4B2', '#EEF1F5', '#28303C'];

/** Reads the live palettes and the HA-colour-name aliases once per session. */
export async function loadStyle(ctx) {
  if (ctx.style) return ctx.style;
  const [data, shipped] = await Promise.all([ctx.ha.call('church_drive/colours').catch(() => ({})), fetch('clive/cards.json').then((r) => r.json()).catch(() => ({}))]);
  const palettes = data.palettes || {}; const names = shipped.entity_colours?.aliases?.names || {};
  ctx.style = {
    /** The five colours (Colour, Dark, Accent, Light, Deep) for a palette key or a Home Assistant colour name. */
    colours: (name) => palettes[name]?.colours || palettes[names[name]]?.colours || palettes.neutral?.colours || NEUTRAL,
  };
  return ctx.style;
}

/** CSS custom properties for a tone: the ground is the palette's Dark colour, with the Clive glow in its Colour and Accent. */
export function toneStyle(ctx, name) {
  const [c, d, a, l] = ctx.style.colours(name);
  return `--ground:${d};--ga:${c};--gb:${a};--accent:${c};--light:${l};`;
}

/** Keeps a Home Assistant template rendered: [apply] gets each new result. The subscription ends when the page changes. */
export function watch(ctx, template, apply) {
  if (!template || !String(template).trim()) return;
  (ctx.cleanups ||= []);
  ctx.ha.subscribe('render_template', { template, strict: false, report_errors: false }, (ev) => apply(ev?.result == null ? '' : String(ev.result)))
    .then((stop) => ctx.cleanups.push(stop)).catch(() => {});
}
export function cleanup(ctx) { (ctx.cleanups || []).splice(0).forEach((stop) => { try { stop(); } catch { /* the line is already down */ } }); }

/** An icon from the house set, our own logos, or (for names we cannot draw) a plain dot. */
export function panelIcon(name, size = 28) {
  const own = /^(?:phu|ol|si):(octopusenergy)$/.exec(name || '');
  if (own && OWN_LOGOS[own[1]]) return `<svg class="ico" width="${size}" height="${size}" viewBox="0 0 24 24" fill="currentColor" fill-rule="evenodd" aria-hidden="true"><path d="${OWN_LOGOS[own[1]]}"/></svg>`;
  if ((name || '').startsWith('cd:')) return icon(name, size);
  return `<svg class="ico" width="${size}" height="${size}" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><circle cx="12" cy="12" r="6"/></svg>`;
}

/** A section panel as the apps draw it: header row (icon, title in the tone's colour, summary pill), then its cards. */
export function panel(ctx, { title, icon: iconName, color, summary, summaryTemplate }, ...content) {
  const pill = h('span', { class: 'pill-summary', hidden: !summary }, summary || '');
  const ico = h('span', { class: 'panel-ico' }); ico.innerHTML = panelIcon(iconName, 30);
  watch(ctx, summaryTemplate, (raw) => { const text = raw.replace(/<[^>]*>/g, ''); pill.textContent = text.trim(); pill.hidden = !text.trim(); });
  return h('section', { class: 'house-panel', style: toneStyle(ctx, color) },
    h('div', { class: 'panel-head' }, ico, h('h2', {}, title), pill), h('div', { class: 'panel-body' }, ...content));
}

/** A card inside a panel: the tone's Dark colour as the ground, white text, the glow behind it. */
export const toneCard = (ctx, color, ...kids) => h('article', { class: 'card tone', style: toneStyle(ctx, color) }, ...kids);
export const fact = (label, value, strong = false) => h('div', { class: 'fact' }, h('span', {}, label), h(strong ? 'b' : 'span', {}, value));
