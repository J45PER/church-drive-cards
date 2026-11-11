// A page laid out from one view of the house's own dashboard (dashboard-mobile), as the apps do: the header lines (live templates), then the section
// panels in the house style. [kinds] says how to draw each custom card the view uses; a card we cannot draw yet is left out.
import { h } from './dom.js';
import { fillIcons } from '../icons.js';
import { cleanup, loadStyle, panel, panelIcon, toneStyle, watch } from '../kit.js';

const tags = (t) => t.replace(/<(?!\/?b>)[^>]*>/g, '');

export async function renderHouseView(el, ctx, { path, what, kinds }) {
  cleanup(ctx);
  el.innerHTML = `<p class="note">Reading the ${what} page from the house…</p>`;
  let view, states;
  try {
    const [config, list] = await Promise.all([ctx.ha.call('lovelace/config', { url_path: 'dashboard-mobile', force: false }), ctx.ha.call('get_states'), loadStyle(ctx)]);
    view = (config.views || []).find((v) => v.path === path);
    states = Object.fromEntries(list.map((s) => [s.entity_id, s]));
  } catch (err) { el.replaceChildren(h('p', { class: 'err', role: 'alert' }, `Could not read the ${what} page: ${err.message}`)); return; }
  const auto = view?.sections?.flatMap((s) => s.cards || []).find((c) => c.type === 'custom:auto-layout-card');
  if (!auto) { el.replaceChildren(h('p', { class: 'err', role: 'alert' }, `The ${what} page is not on the house dashboard yet.`)); return; }

  const lines = (auto.header_lines || []).map((l) => {
    const text = h('span', { class: 'line-text' }), ico = h('span', { class: 'panel-ico' });
    const paint = (colour, name) => { ico.setAttribute('style', toneStyle(ctx, colour)); ico.innerHTML = panelIcon(name, 20); fillIcons(ico); };
    paint(l.color, l.icon);
    watch(ctx, l.text, (t) => { text.innerHTML = tags(t); });
    if (l.alert_when) watch(ctx, l.alert_when, (v) => (v.trim().toLowerCase() === 'true' ? paint(l.alert_color || 'warn', l.alert_icon || l.icon) : paint(l.color, l.icon)));
    return h('div', { class: 'line-row' }, ico, text);
  });
  const panels = (auto.cards || []).filter((c) => c.type === 'custom:section-panel-card').map((c) => {
    const tone = c.color || 'neutral';
    const inner = (c.cards || []).map((k) => kinds[k.type]?.(ctx, k, states, tone)).filter(Boolean);
    return panel(ctx, { title: c.title, icon: c.icon, color: tone, summaryTemplate: c.summary, colorTemplate: c.color_template }, ...inner);
  });
  el.replaceChildren(h('div', { class: 'stack' }, h('div', { class: 'header-lines' }, lines), ...panels));
  fillIcons(el);
}
