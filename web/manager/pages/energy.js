// The website's Energy page, laid out from the house's own dashboard (dashboard-mobile, "energy" view) like the apps do:
// the header lines, then the Octopus Electricity, Gas, Last 7 days, Car charging and Octoplus panels in the house style.
import { h } from './dom.js';
import { energySnapshot, clock } from './logic.js';
import { renderHouseView } from './house-view.js';
import { fact, toneCard } from '../kit.js';

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

const KINDS = {
  'custom:octopus-card': (ctx, k, states, tone) => octopusCard(ctx, k.show, energySnapshot(states), tone),
  'custom:car-card': (ctx, k, states, tone) => noted(ctx, tone, 'Your cars and their charge are in the Church Drive and Clive Admin apps.'),
};
export const renderEnergyPage = (el, ctx) => renderHouseView(el, ctx, { path: 'energy', what: 'energy', kinds: KINDS });
