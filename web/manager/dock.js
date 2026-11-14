// The bottom dock, as on the Home Assistant dashboard: a floating bar of round icons, each in its page's colour, the current page filled and opened out into a pill with
// its name, a dot when something needs a look (Device health, Edits), and a button to tuck it away. Pages with sections put a smaller bar above it (ctx.setSub).
import { ic, palette, readableOn } from './ui.js';

const SHORT = { home: 'Home', design: 'Design', clive: 'Clive', reference: 'Reference', manager: 'Settings', changes: 'Edits' };
const TONE = { home: 'tasks', design: 'tasks', reference: 'gregg', clive: 'people', manager: 'warn', changes: 'tasks' };
let tucked = false;

/** The dots: Device health when something needs attention (red when it is wrong), Edits while something waits for Clive. Read from two sensors the house keeps. */
export async function dockAlerts(ctx) {
  const out = {};
  try {
    const st = await ctx.ha.call('get_states'), by = Object.fromEntries(st.filter((e) => /^sensor\.church_drive_proposals$/.test(e.entity_id)).map((e) => [e.entity_id, e]));
    const p = Number(by['sensor.church_drive_proposals']?.state);
    if (p > 0) out.changes = '#FFB74D';
  } catch { /* no dots */ }
  return out;
}

export function dockHtml(ctx, pages, current, alerts = {}) {
  const dark = document.documentElement.dataset.theme ? document.documentElement.dataset.theme === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
  const neutral = palette(ctx, 'neutral'), items = []; let last = null;
  for (const p of pages) {
    const grp = p.group || null;
    if (last !== null && grp !== last) items.push('<span class="dsep"></span>'); last = grp;
    const t = palette(ctx, TONE[p.id] || 'neutral'), ac = dark ? readableOn(t[0], t[3], neutral[1]) : readableOn(t[0], t[4], '#E7EAF0'), on = p.id === current, a = alerts[p.id];
    items.push(`<a href="#${p.id === 'home' ? '' : p.id}" class="di${on ? ' on' : ''}" data-page="${p.id}" style="--t:${ac};--dk:${dark ? t[1] : '#fff'};${a ? `--dc:${a}` : ''}" title="${SHORT[p.id] || p.label}" aria-label="${SHORT[p.id] || p.label}"${on ? ' aria-current="page"' : ''}>${ic(p.icon.replace(/^cd:/, ''), 24)}${on ? `<span class="dlab">${SHORT[p.id] || p.label}</span>` : ''}${a ? '<i class="dot"></i>' : ''}</a>`);
  }
  return `<div class="dockw"><div id="subdock"></div><div class="dockrow">${tucked ? '' : `<nav class="dock" aria-label="Pages">${items.join('')}</nav>`}<button type="button" class="dockc" data-dock aria-label="${tucked ? 'Show navigation' : 'Hide navigation'}">${tucked ? ic('home', 22) : '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="5" y="11" width="14" height="2.4" rx="1.2"/></svg>'}</button></div></div>`;
}

export function bindDock(root, redraw) {
  root.querySelector('[data-dock]')?.addEventListener('click', () => { tucked = !tucked; redraw(); });
  requestAnimationFrame(() => requestAnimationFrame(() => root.querySelectorAll('.dlab').forEach((e) => e.classList.add('in'))));
  root.addEventListener('click', (e) => { const b = e.target.closest('[data-gjump]'); if (b) document.getElementById(`grp-${b.dataset.gjump}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }); });
}
