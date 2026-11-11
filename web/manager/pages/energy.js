// The website's Energy page, laid out from the house's own dashboard (dashboard-mobile, "energy" view) like the apps do:
// the header lines, then the Octopus Electricity, Gas, Last 7 days, Car charging and Octoplus panels in the house style.
import { h } from './dom.js';
import { energySnapshot, clock } from './logic.js';
import { fillIcons } from '../icons.js';
import { cleanup, fact, loadStyle, panel, panelIcon, toneCard, watch } from '../kit.js';

const p = (n, d = 1) => (n == null ? '—' : `${n.toFixed(d)}p`);
const pound = (n) => (n == null ? '—' : `£${n.toFixed(2)}`);
const html = (el, text) => { el.innerHTML = text; return el; };

/** The strip of today's and tomorrow's half-hour rates: cheapest in the accent colour, now in white. */
function strip(e) {
  if (!e.rates.length) return h('p', { class: 'sub' }, 'Rates appear once Octopus has published them.');
  const max = Math.max(...e.rates.map((r) => r.pence), 1), now = Date.now();
  return h('div', { class: 'strip', role: 'img', 'aria-label': 'Electricity rates, half hour by half hour' },
    e.rates.map((r) => { const b = h('i', { class: r.start <= now && now < r.end ? 'now' : r.pence <= (e.cheapest ?? -1) + 0.01 ? 'cheap' : '' }); b.style.height = `${Math.max(6, (r.pence / max) * 100)}%`; return b; }));
}

/** One card of an Octopus panel, from the figures the house already has. */
function octopusCard(ctx, show, e, tone) {
  if (show === 'electricity') {
    const cheap = e.cheapUntil ? `Cheap until ${clock(e.cheapUntil)}` : e.offPeak ? 'Off-peak now' : e.cheapest != null ? `Cheapest today ${p(e.cheapest)}` : '';
    return toneCard(ctx, tone, h('p', { class: 'sub' }, 'Right now'), h('p', { class: 'big' }, p(e.rate)), h('p', { class: 'sub' }, [cheap, e.demand != null ? `${Math.round(e.demand)} W` : ''].filter(Boolean).join(' · ')),
      strip(e), fact('Today', `${pound(e.today)}${e.todayKwh != null ? ` · ${e.todayKwh.toFixed(1)} kWh` : ''}`), fact('Yesterday', `${pound(e.yesterday)}${e.yesterdayKwh != null ? ` · ${e.yesterdayKwh.toFixed(1)} kWh` : ''}`));
  }
  if (show === 'gas') return toneCard(ctx, tone, h('p', { class: 'sub' }, 'Rate'), h('p', { class: 'big' }, e.gasRate != null ? `${p(e.gasRate, 2)} per kWh` : '—'), fact('Today', pound(e.gasToday)), fact('Yesterday', pound(e.gasYesterday)));
  if (show === 'octoplus') return toneCard(ctx, tone, h('p', { class: 'sub' }, 'Octoplus points'), h('p', { class: 'big' }, e.points != null ? String(Math.round(e.points)) : '—'));
  if (show === 'week') return toneCard(ctx, tone, h('p', { class: 'sub' }, 'Electricity and gas over the last seven days'), h('p', { class: 'sub' }, 'The day-by-day figures are in the apps; the total is in the heading.'));
  return null;
}

/** Cars and the charger are drawn in the apps; here the charge mode stands for them. */
const noted = (ctx, tone, text) => toneCard(ctx, tone, h('p', { class: 'sub' }, text));

export async function renderEnergyPage(el, ctx) {
  cleanup(ctx);
  el.innerHTML = '<p class="note">Reading the energy page from the house…</p>';
  let view, e;
  try {
    const [config, states] = await Promise.all([ctx.ha.call('lovelace/config', { url_path: 'dashboard-mobile', force: false }), ctx.ha.call('get_states'), loadStyle(ctx)]);
    view = (config.views || []).find((v) => v.path === 'energy');
    e = energySnapshot(Object.fromEntries(states.map((s) => [s.entity_id, s])));
  } catch (err) { el.innerHTML = ''; el.append(h('p', { class: 'err', role: 'alert' }, `Could not read the energy page: ${err.message}`)); return; }
  const section = view?.sections?.[0]; const auto = section?.cards?.find((c) => c.type === 'custom:auto-layout-card');
  if (!auto) { el.innerHTML = ''; el.append(h('p', { class: 'err', role: 'alert' }, 'The energy page is not on the house dashboard yet.')); return; }

  const lines = (auto.header_lines || []).map((l) => {
    const text = h('span', { class: 'line-text' }); const ico = h('span', { class: 'panel-ico', style: `--accent:${ctx.style.colours(l.color)[0]}` });
    ico.innerHTML = panelIcon(l.icon, 20);
    const row = h('div', { class: 'line-row' }, ico, text);
    watch(ctx, l.text, (t) => { text.innerHTML = t.replace(/<(?!\/?b>)[^>]*>/g, ''); });
    return row;
  });
  const panels = (auto.cards || []).map((c) => {
    const tone = c.color || 'energy';
    const inner = (c.cards || []).map((k) => {
      if (k.type === 'custom:octopus-card') return octopusCard(ctx, k.show, e, tone);
      if (k.type === 'custom:car-card') return noted(ctx, tone, 'Your cars and their charge are in the Church Drive and Clive Admin apps.');
      return null;
    }).filter(Boolean);
    return panel(ctx, { title: c.title, icon: c.icon, color: tone, summaryTemplate: c.summary }, ...inner);
  });
  el.innerHTML = '';
  el.append(h('div', { class: 'stack' }, h('div', { class: 'header-lines' }, lines), ...panels));
  fillIcons(el);
}
