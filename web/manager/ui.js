// The house style on the website, from the example site the owner signed off page by page (docs/STYLE.md): tone cards with the Clive glow, the neutral
// header pills (a circle with the icon knocked out), acrylic surfaces, and the app's button and colour rules. Pages build HTML strings with these
// helpers, then call fillIcons() on what they drew. Text from the house always goes through esc().
import { icon } from './icons.js';
import { panelIcon } from './kit.js';
import { contrast } from './pages/logic.js';

export const esc = (t) => String(t ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
export const NA = '<span class="na">Data not available</span>';

const rgb = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
const toHex = (c) => `#${c.map((v) => Math.round(v).toString(16).padStart(2, '0')).join('')}`;
export const mixHex = (a, b, k) => { const x = rgb(a), y = rgb(b); return toHex(x.map((v, i) => v + (y[i] - v) * k)); };
export function lumOf(h) { const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; }; const [r, g, b] = rgb(h); return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); }

/** The app's readableOn (StateColors.kt): the palette Colour as it is when it reads on the card's Dark (4.5:1), else lifted towards its Light just far enough. */
export function readableOn(colour, toward, on, min = 4.5) {
  if (contrast(colour, on) >= min) return colour;
  for (let i = 1; i <= 20; i++) { const m = mixHex(colour, toward, i / 20); if (contrast(m, on) >= min) return m; }
  return toward;
}

/** The five colours (Colour, Dark, Accent, Light, Deep) of a palette key, with Clive's own tone (the base colours of his glow). */
export function palette(ctx, key) {
  if (key === 'clive') return ['#5C6BC0', '#1E2345', '#AB47BC', '#C5CAE9', '#283593'];
  return ctx.style.colours(key);
}
/** CSS variables of a tone card: the ground (Dark), the glow colours (Colour, Accent) and the strong colour that reads on the ground. */
export function toneVars(ctx, key) { const [c, d, a, l] = palette(ctx, key); return `--d:${d};--a:${c};--b:${a};--ac:${readableOn(c, l, d)}`; }
/** The colour of a status circle in a neutral pill: a palette's strong colour. */
export function circ(ctx, key) { const [c, , , l] = palette(ctx, key); return readableOn(c, l, palette(ctx, 'neutral')[1]); }

/** A house icon (`cd:` name without the prefix), or an own logo as `logo:octopusenergy`. Call fillIcons() on what it ends up in. */
export function ic(name, size = 24) {
  if (name.startsWith('logo:')) return panelIcon(`ol:${name.slice(5)}`, size);
  return icon(name.includes(':') ? name : `cd:${name}`, size);
}

/** A header pill: neutral ground, a white circle with the icon knocked out (the circle takes a status colour when [circle] is given). */
export function pill(ctx, iconName, html, circle, tag = 'div', attrs = '') {
  return `<${tag} class="hl" ${attrs}><span class="pi"${circle ? ` style="background:${circle}"` : ''}>${ic(iconName, 18)}</span><span>${html}</span></${tag}>`;
}
/** A tone card with its icon, title and an optional status pill in the header. */
export function wcard(ctx, tone, iconName, title, status, body, cls = '') {
  return `<article class="tcard glow wc ${cls}" style="${toneVars(ctx, tone)}"><div class="wc-h"><span class="wc-i">${ic(iconName, 26)}</span><h2>${title}</h2>${status ? `<span class="wc-p">${status}</span>` : ''}</div>${body}</article>`;
}
export const fct = (a, b) => `<div class="fct"><span>${a}</span><b>${b}</b></div>`;
export const tagp = (text, kind = '') => `<span class="tagp ${kind}">${text}</span>`;
export const hhmm = (ms) => new Date(ms).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
export const gbp = (v) => (v == null ? '–' : `£${v.toFixed(2)}`);
export const pf = (v, d = 1) => (v == null ? '–' : `${v.toFixed(d)}p`);
export const kwhf = (v) => (v == null ? '' : `${v.toFixed(1)} kWh`);
export const slug = (s) => String(s).toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
export const states = async (ctx) => Object.fromEntries((await ctx.ha.call('get_states')).map((e) => [e.entity_id, e]));

/** Makes [win] draggable by [bar] and keeps the place in [pos]; buttons and fields in the bar still work. */
export function dragWindow(win, bar, pos) {
  bar.addEventListener('pointerdown', (e) => {
    if (e.target.closest('button,input')) return;
    const r = win.getBoundingClientRect(), dx = e.clientX - r.left, dy = e.clientY - r.top;
    bar.setPointerCapture(e.pointerId); win.classList.add('drag');
    const move = (m) => {
      const x = Math.min(innerWidth - 80, Math.max(-r.width + 120, m.clientX - dx)), y = Math.min(innerHeight - 48, Math.max(0, m.clientY - dy));
      win.style.left = `${x}px`; win.style.top = `${y}px`; pos.x = x; pos.y = y;
    };
    const up = () => { win.classList.remove('drag'); bar.removeEventListener('pointermove', move); bar.removeEventListener('pointerup', up); bar.removeEventListener('pointercancel', up); };
    bar.addEventListener('pointermove', move); bar.addEventListener('pointerup', up); bar.addEventListener('pointercancel', up);
  });
}
