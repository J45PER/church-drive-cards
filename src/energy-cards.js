// Energy cards for Octopus Energy (the BottlecapDave integration) and a
// myenergi Zappi car charger. Both find their entities by themselves.
//
// Octopus Card, one of four views (`show`):
// - electricity: the rate this half-hour (Cheap or Peak), live use from a
//   Home Mini, today's running cost, and a 24-hour strip of today's rates
//   with the cheap window and how long until it starts;
// - week: the last seven finished days, from the long-term statistics the
//   integration keeps (so days Octopus sends late fill in): a bar per day in
//   the electricity and gas colours, the week's electricity/gas balance, a
//   day's cost and kWh and how much ran at the cheap rate, and how far behind
//   Octopus is;
// - gas: the rate, standing charge and today so far;
// - octoplus: points, weekend happy hours and saving sessions;
// - cheap: a strip for everyone: cheap rate now (until when) or when it next
//   starts, and the next Octoplus power-down session (prices for admins only).
//
// EV Charger Card: a Zappi's state, power, this charge and mode buttons
// (Stop, Eco, Eco+, Fast). Until the myenergi integration is set up it says
// so and waits. With "Zappi smart charge" set up in Home Assistant (see
// ha/zappi-smart-charge.yaml) it also has a Smart charge switch (charge at
// the cheapest Octopus rate by itself), and pressing Start charge or Fast at
// the normal rate offers "Charge at 00:30" or "Charge now". Right after Stop
// it says "Stopping…" until the charger's readings catch up.

import { octopusPlan, planLine } from './car-octopus.js';
import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { KIT_COLOR, kitShell, kitHead, kitTiles, kitNum, kitCap, kitEsc, kitCompact, kitCompactable } from './card-kit.js';
import { iconFor, watchIcons } from './icon-library.js';
import { openPopup } from './popup.js';
import { weekStatIds, buildWeek, dayCheapShare, lagNote, balance } from './energy-week.js';

// Octopus's own pink, and the charger's teal.
export const OCTO_PINK = '#f050f8';
// Octopus's violet, for gas.
export const OCTO_VIOLET = '#7b61ff';
const EV_TEAL = '#00b8d4';
const CHEAP = KIT_COLOR.good;
const PEAK = KIT_COLOR.poor;

const pence = (gbp) => (gbp == null ? '–' : `${(gbp * 100).toFixed(gbp * 100 < 10 ? 2 : 1).replace(/\.?0+$/, '')}p`);
const pounds = (gbp) => (gbp == null ? '–' : `£${Number(gbp).toFixed(2)}`);
const hhmm = (d) => d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
const span = (ms) => {
  const m = Math.max(0, Math.round(ms / 60e3));
  return m < 60 ? `${m} min` : `${Math.floor(m / 60)} h ${m % 60 ? `${m % 60} m` : ''}`.trim();
};

// Entities an integration made, from the frontend's entity registry (or by
// their ids if that isn't available).
function platformEntities(hass, platform, idHint) {
  const reg = hass.entities || {};
  const ids = Object.keys(hass.states).filter((id) => (reg[id] ? reg[id].platform === platform : id.includes(idHint)));
  return ids;
}

// Today's and tomorrow's half-hour rates for a meter, and which count as cheap (the day's lowest, when rates differ).
export function ratesOf(hass, m) {
  const get = (k) => {
    const e = m.elec && hass.states[`event.${m.elec}${k}`];
    return (e && e.attributes.rates) || [];
  };
  const all = [...get('current_day_rates'), ...get('next_day_rates')]
    .map((r) => ({ start: new Date(r.start), end: new Date(r.end), v: Number(r.value_inc_vat) }))
    .filter((r) => !isNaN(r.start) && !isNaN(r.v))
    .sort((a, b) => a.start - b.start);
  const min = all.length ? Math.min(...all.map((r) => r.v)) : null;
  const max = all.length ? Math.max(...all.map((r) => r.v)) : null;
  const cheap = (v) => min != null && max != null && max - min > 0.001 && v <= min + 0.001;
  return { all, cheap, min };
}

// "Cheap now until 05:30" or the next cheap window ({ now, from, until, v }), or null.
export function windowOf(rates) {
  const now = Date.now();
  const i = rates.all.findIndex((r) => r.start <= now && r.end > now);
  if (i < 0) return null;
  const runEnd = (k) => {
    let j = k;
    while (j + 1 < rates.all.length && rates.cheap(rates.all[j + 1].v) && +rates.all[j + 1].start === +rates.all[j].end) j += 1;
    return rates.all[j].end;
  };
  if (rates.cheap(rates.all[i].v)) return { now: true, until: runEnd(i), v: rates.all[i].v };
  const k = rates.all.findIndex((r, n) => n > i && rates.cheap(r.v));
  if (k < 0) return null;
  return { now: false, from: rates.all[k].start, until: runEnd(k), v: rates.all[k].v };
}

// The Octopus meters: { elec: 'octopus_energy_electricity_<mpan>_<serial>_', gas: …, account: … }.
export function octoFind(hass) {
  const ids = platformEntities(hass, 'octopus_energy', 'octopus_energy_');
  const find = (re, tail) => {
    const id = ids.find((x) => re.test(x));
    return id ? id.split('.')[1].slice(0, -tail.length) : null;
  };
  return {
    elec: find(/^sensor\.octopus_energy_electricity_.*_current_rate$/, 'current_rate'),
    gas: find(/^sensor\.octopus_energy_gas_.*_current_rate$/, 'current_rate'),
    account: find(/^sensor\.octopus_energy_a_.*_octoplus_points$/, 'octoplus_points'),
  };
}

const OCTO_VIEWS = [
  { value: 'electricity', label: 'Electricity now' },
  { value: 'week', label: 'Last 7 days (electricity and gas)' },
  { value: 'gas', label: 'Gas' },
  { value: 'octoplus', label: 'Octoplus' },
  { value: 'cheap', label: 'Cheap rate and power-down sessions' },
];

export const OctopusCardEditor = createFormEditor({
  schema: () => [
    { name: 'show', selector: { select: { mode: 'dropdown', options: OCTO_VIEWS } } },
    { name: 'name', selector: { text: {} } },
  ],
  labels: { show: 'Show', name: 'Title (optional)' },
  helpers: { show: 'Finds your Octopus meters by itself (the Octopus Energy integration). Live use needs a Home Mini.' },
});

export class OctopusCard extends HTMLElement {
  setConfig(config) {
    // "Last full day" became "Last 7 days": cards set to the old one show the new one.
    this.config = { show: 'electricity', ...config };
    if (this.config.show === 'last_day') this.config.show = 'week';
    this._built = false;
    this._sig = null;
  }

  set hass(hass) {
    this._hass = hass;
    this._render();
  }

  connectedCallback() {
    // The countdown and "now" line move on their own.
    this._tick = setInterval(() => {
      this._sig = null;
      this._render();
    }, 60e3);
  }

  disconnectedCallback() {
    clearInterval(this._tick);
  }

  _s(obj, key, domain = 'sensor') {
    return obj ? this._hass.states[`${domain}.${obj}${key}`] : null;
  }

  // Today's and tomorrow's half-hour rates, and which count as cheap.
  _rates(m) {
    return ratesOf(this._hass, m);
  }

  // "Cheap now until 05:30" or the next cheap window and how long until it.
  _window(rates) {
    return windowOf(rates);
  }

  _render() {
    if (!this._hass) return;
    const c = this.config;
    const m = octoFind(this._hass);
    const view = c.show;
    if (this._compact) return kitCompact(this, this._compactSpec(m));
    if (!this._built) {
      this.innerHTML = kitShell(`<div class="oc-body" style="display:flex; flex-direction:column; gap:10px;"></div>`, `
        .oc-big { display:flex; align-items:baseline; gap:10px; }
        .oc-big b { font-size:2.2rem; font-weight:300; font-variant-numeric:tabular-nums; line-height:1.1; }
        .oc-two { display:grid; grid-template-columns:1fr 1fr; gap:8px; }
        .oc-stat { border-radius:12px; background:rgba(127,127,127,.12); padding:10px 12px; display:flex; flex-direction:column; gap:2px; min-width:0; }
        .oc-stat b { font-size:1.15rem; font-weight:600; font-variant-numeric:tabular-nums; }
        .oc-stat span { font-size:.72rem; color:var(--secondary-text-color); }
        .oc-strip { position:relative; height:20px; border-radius:8px; overflow:hidden; display:flex; }
        .oc-strip i { display:block; height:100%; }
        .oc-now { position:absolute; top:-2px; bottom:-2px; width:2px; background:var(--primary-text-color); box-shadow:0 0 0 2px rgba(0,0,0,.35); }
        .oc-ticks { display:flex; justify-content:space-between; font-size:.68rem; color:var(--secondary-text-color); font-variant-numeric:tabular-nums; }
        .oc-bars { display:flex; align-items:flex-end; gap:1px; height:52px; }
        .oc-bars i { flex:1; border-radius:2px 2px 0 0; min-height:2px; }
        .oc-meter { height:8px; border-radius:99px; background:rgba(127,127,127,.2); overflow:hidden; display:flex; }
        .oc-meter i { display:block; height:100%; }
        .oc-pill { margin-left:auto; font-size:.72rem; font-weight:700; padding:2px 9px; border-radius:999px; }
        .oc-days { display:grid; grid-template-columns:repeat(7, 1fr); gap:6px; }
        .oc-day { border:none; background:none; padding:4px 0; border-radius:10px; cursor:pointer; color:var(--primary-text-color); font:inherit; display:flex; flex-direction:column; align-items:center; gap:4px; min-width:0; }
        .oc-day.oc-sel { background:rgba(127,127,127,.18); }
        .oc-day:focus-visible { outline:2px solid var(--primary-color); outline-offset:2px; }
        .oc-amt { font-size:.68rem; font-variant-numeric:tabular-nums; color:var(--secondary-text-color); }
        .oc-col { width:70%; max-width:34px; height:64px; display:flex; flex-direction:column-reverse; justify-content:flex-start; border-radius:6px 6px 3px 3px; overflow:hidden; }
        .oc-col i { display:block; width:100%; }
        .oc-col i.oc-gap { height:100%; border:1px dashed rgba(127,127,127,.5); background:none; border-radius:6px 6px 3px 3px; box-sizing:border-box; }
        .oc-dn { font-size:.7rem; font-weight:600; }
        .oc-bal { height:12px; border-radius:99px; overflow:hidden; display:flex; background:rgba(127,127,127,.2); }
        .oc-bal i { display:block; height:100%; }
        .oc-legend { display:flex; justify-content:space-between; gap:8px; font-size:.78rem; flex-wrap:wrap; }`);
      this._body = this.querySelector('.oc-body');
      this._body.addEventListener('click', (ev) => {
        const b = ev.target.closest && ev.target.closest('[data-day]');
        if (!b) return;
        this._selDay = b.dataset.day;
        this._sig = null;
        this._render();
      });
      this._built = true;
    }
    const minute = Math.floor(Date.now() / 60e3);
    const html = view === 'week' ? this._week(m) : view === 'gas' ? this._gas(m) : view === 'octoplus' ? this._octoplus(m) : view === 'cheap' ? this._cheap(m) : this._elec(m);
    const sig = JSON.stringify([html.head, html.body, minute]);
    if (sig === this._sig) return;
    this._sig = sig;
    kitHead(this, html.head[0], html.head[1], html.head[2]);
    this._body.innerHTML = html.body;
    hydrateIcons(this);
  }

  _missing(what) {
    return { head: [this.config.name || 'Octopus', 'Not set up', KIT_COLOR.off], body: `<div class="ck-sub" style="line-height:1.5;">No Octopus ${what} found. Add the Octopus Energy integration (from HACS) and this card finds your meters by itself.</div>` };
  }

  _elec(m) {
    if (!m.elec) return this._missing('electricity meter');
    const rate = kitNum(this._s(m.elec, 'current_rate'));
    const rates = this._rates(m);
    const cheapNow = rate != null && rates.cheap(rate);
    const col = cheapNow ? CHEAP : PEAK;
    const demand = kitNum(this._s(m.elec, 'current_demand'));
    const todayCost = kitNum(this._s(m.elec, 'current_accumulative_cost'));
    const todayKwh = kitNum(this._s(m.elec, 'current_accumulative_consumption'));
    const standing = kitNum(this._s(m.elec, 'current_standing_charge'));
    const tariff = (this._s(m.elec, 'current_rate') || { attributes: {} }).attributes.tariff || '';
    const w = this._window(rates);
    // Today's strip, midnight to midnight.
    const d0 = new Date();
    d0.setHours(0, 0, 0, 0);
    const d1 = +d0 + 86400e3;
    const today = rates.all.filter((r) => r.start >= d0 && r.start < d1);
    const strip = today.map((r) => `<i style="width:${((r.end - r.start) / 864e5) * 100}%; background:${rates.cheap(r.v) ? CHEAP : `color-mix(in srgb, ${PEAK} 55%, transparent)`};"></i>`).join('');
    const nowPct = ((Date.now() - d0) / 864e5) * 100;
    const win = !w ? '' : w.now
      ? `<b style="color:${CHEAP};">Cheap now</b> at ${pence(w.v)} until ${hhmm(w.until)}`
      : `<b style="color:${CHEAP};">${pence(w.v)}</b> from ${hhmm(w.from)} to ${hhmm(w.until)}, in ${span(w.from - Date.now())}`;
    const product = /GO/.test(tariff) ? 'Octopus Go' : /AGILE/.test(tariff) ? 'Agile Octopus' : /INTELLI/.test(tariff) ? 'Intelligent Octopus' : '';
    return {
      head: [this.config.name || 'Octopus Electricity', rate == null ? 'No rate' : `${pence(rate)} · ${cheapNow ? 'Cheap' : 'Peak'}`, OCTO_PINK],
      body: `
        <div class="oc-big"><b style="color:${col};">${pence(rate)}</b><span class="ck-sub">per kWh now</span><span class="oc-pill" style="background:color-mix(in srgb, ${col} 22%, transparent); color:${col};">${cheapNow ? 'Cheap' : 'Peak'}</span></div>
        ${demand != null || todayCost != null ? `<div class="oc-two">
          <div class="oc-stat"><b>${demand == null ? '–' : `${Math.round(demand)} W`}</b><span>Using now</span></div>
          <div class="oc-stat"><b>${pounds(todayCost)}</b><span>Today so far${todayKwh != null ? ` · ${todayKwh.toFixed(1)} kWh` : ''}</span></div></div>` : ''}
        ${today.length ? `<div class="oc-strip">${strip}<div class="oc-now" style="left:${nowPct.toFixed(2)}%;"></div></div>
        <div class="oc-ticks"><span>00:00</span><span>06:00</span><span>12:00</span><span>18:00</span><span>24:00</span></div>` : ''}
        <div class="ck-sub">${[win, product, standing != null ? `standing charge ${pence(standing)} a day` : ''].filter(Boolean).join(' · ')}</div>`,
    };
  }

  // The last seven finished days, read from the long-term statistics the Octopus integration keeps in Home Assistant (the
  // ones the Energy dashboard uses), so every day is there, including ones Octopus sent late.
  _weekData(m) {
    const ids = weekStatIds(m);
    const key = Object.values(ids).join(',');
    const have = this._wk && this._wk.key === key;
    if ((!have || Date.now() - this._wk.at > 30 * 60e3) && !this._wkBusy && key) this._loadWeek(ids, key);
    return have ? { ids, wk: this._wk } : null;
  }

  async _loadWeek(ids, key) {
    this._wkBusy = true;
    try {
      const start = new Date();
      start.setDate(start.getDate() - 10);
      start.setHours(0, 0, 0, 0);
      const result = await this._hass.callWS({ type: 'recorder/statistics_during_period', start_time: start.toISOString(), statistic_ids: Object.values(ids), period: 'day', types: ['change'] });
      this._wk = { key, at: Date.now(), result };
    } catch (err) {
      this._wk = { key, at: Date.now(), error: (err && err.message) || 'failed' };
    }
    this._wkBusy = false;
    this._sig = null;
    this._render();
  }

  _week(m) {
    const name = this.config.name || 'Last 7 days';
    if (!m.elec && !m.gas) return this._missing('meter');
    const got = this._weekData(m);
    if (!got) return { head: [name, 'Loading…', KIT_COLOR.off], body: '<div class="ck-sub">Reading the Octopus history…</div>' };
    if (got.wk.error) return { head: [name, 'Not available', KIT_COLOR.off], body: `<div class="ck-sub" style="line-height:1.5;">Couldn't read the Octopus history from Home Assistant (${kitEsc(got.wk.error)}).</div>` };
    const tz = this._hass.config && this._hass.config.time_zone;
    const w = buildWeek(got.wk.result, got.ids, Date.now(), 7, tz);
    const label = (iso) => new Date(`${iso}T12:00:00`).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
    const dow = (iso) => new Date(`${iso}T12:00:00`).toLocaleDateString(undefined, { weekday: 'short' });
    if (!w.days.some((d) => d.elecCost != null || d.gasCost != null)) {
      return { head: [name, 'Waiting for Octopus', KIT_COLOR.off], body: '<div class="ck-sub" style="line-height:1.5;">Octopus hasn\'t sent any of the last seven days yet.</div>' };
    }
    const sel = w.days.find((d) => d.date === this._selDay) || w.days.find((d) => d.date === w.latestWithData);
    const total = (d) => (d.elecCost || 0) + (d.gasCost || 0);
    const max = Math.max(...w.days.map(total), 0.01);
    const bars = w.days
      .map((d) => {
        const has = d.elecCost != null || d.gasCost != null;
        const col = has
          ? `<i style="height:${((d.gasCost || 0) / max) * 100}%; background:${OCTO_VIOLET};"></i><i style="height:${((d.elecCost || 0) / max) * 100}%; background:${OCTO_PINK};"></i>`
          : '<i class="oc-gap"></i>';
        return `<button type="button" class="oc-day${d === sel ? ' oc-sel' : ''}" data-day="${d.date}" aria-label="${kitEsc(label(d.date))}: ${has ? pounds(total(d)) : 'no data yet'}"><span class="oc-amt">${has ? pounds(total(d)) : '–'}</span><span class="oc-col">${col}</span><span class="oc-dn">${dow(d.date)}</span></button>`;
      })
      .join('');
    const bal = balance(w);
    const share = dayCheapShare(sel);
    const note = lagNote(w, label);
    const kwh = (v) => (v == null ? '' : ` · ${v.toFixed(1)} kWh`);
    return {
      head: [name, `${pounds(w.cost)} · ${pounds(w.average)} a day`, KIT_COLOR.comfy],
      body: `
        <div class="oc-days">${bars}</div>
        ${bal ? `<div class="oc-bal" role="img" aria-label="Electricity ${bal.elec}%, gas ${bal.gas}%"><i style="width:${bal.elec}%; background:${OCTO_PINK};"></i><i style="width:${bal.gas}%; background:${OCTO_VIOLET};"></i></div>
        <div class="oc-legend"><span><b style="color:${OCTO_PINK};">Electricity ${pounds(w.elecCost)}</b> · ${bal.elec}%${kwh(w.elecKwh)}</span><span><b style="color:${OCTO_VIOLET};">Gas ${pounds(w.gasCost)}</b> · ${bal.gas}%${kwh(w.gasKwh)}</span></div>` : ''}
        <div class="ck-sub"><b>${kitEsc(label(sel.date))}</b></div>
        <div class="oc-two">
          <div class="oc-stat"><b style="color:${OCTO_PINK};">${sel.elecCost == null ? '–' : pounds(sel.elecCost)}</b><span>Electricity${kwh(sel.elecKwh)}</span></div>
          <div class="oc-stat"><b style="color:${OCTO_VIOLET};">${sel.gasCost == null ? '–' : pounds(sel.gasCost)}</b><span>Gas${kwh(sel.gasKwh)}</span></div>
        </div>
        ${share != null ? `<div class="oc-meter"><i style="width:${share}%; background:${CHEAP};"></i><i style="width:${100 - share}%; background:${PEAK};"></i></div>
        <div class="ck-sub"><b style="color:${CHEAP};">${share}%</b> of that day's electricity was used at the cheap rate.</div>` : ''}
        ${note ? `<div class="ck-sub" style="color:#ffa726;">${kitEsc(note)}</div>` : ''}`,
    };
  }

  _gas(m) {
    if (!m.gas) return this._missing('gas meter');
    const rate = kitNum(this._s(m.gas, 'current_rate'));
    const standing = kitNum(this._s(m.gas, 'current_standing_charge'));
    const kwh = kitNum(this._s(m.gas, 'current_accumulative_consumption_kwh'));
    const cost = kitNum(this._s(m.gas, 'current_accumulative_cost'));
    return {
      head: [this.config.name || 'Gas', rate == null ? 'No rate' : `${pence(rate)} per kWh`, OCTO_VIOLET],
      body: `
        <div class="oc-big"><b>${pence(rate)}</b><span class="ck-sub">per kWh</span></div>
        ${kwh != null || cost != null ? `<div class="oc-two"><div class="oc-stat"><b>${pounds(cost)}</b><span>Today so far${kwh != null ? ` · ${kwh.toFixed(1)} kWh` : ''}</span></div><div class="oc-stat"><b>${pence(standing)}</b><span>Standing charge a day</span></div></div>` : `<div class="ck-sub">Standing charge ${pence(standing)} a day</div>`}`,
    };
  }

  _octoplus(m) {
    if (!m.account) return this._missing('account');
    const pts = kitNum(this._s(m.account, 'octoplus_points'));
    const happy = kitNum(this._s(m.account, 'octoplus_weekend_happy_hours'));
    const cal = this._hass.states[`calendar.${m.account}octoplus_power_down`];
    const up = this._hass.states[`calendar.${m.account}octoplus_power_up`];
    const ev = (x) => (x && x.attributes.start_time ? `${x.attributes.message || 'Session'} · ${new Date(x.attributes.start_time).toLocaleString(undefined, { weekday: 'short', hour: '2-digit', minute: '2-digit' })}` : '');
    const sessions = [ev(cal), ev(up)].filter(Boolean);
    return {
      head: [this.config.name || 'Octoplus', pts == null ? '' : `${pts} points`, OCTO_PINK],
      body: `
        <div class="oc-two"><div class="oc-stat"><b>${pts == null ? '–' : pts}</b><span>Points</span></div><div class="oc-stat"><b>${happy == null ? '–' : happy}</b><span>Weekend happy hours</span></div></div>
        <div class="ck-sub">${sessions.length ? `Saving sessions: ${kitEsc(sessions.join(', '))}` : 'No saving sessions booked.'}</div>`,
    };
  }

  _cheap(m) {
    if (!m.elec) return this._missing('electricity meter');
    const admin = !!(this._hass.user && this._hass.user.is_admin);
    const w = this._window(this._rates(m));
    const cal = m.account ? this._hass.states[`calendar.${m.account}octoplus_power_down`] : null;
    const pd = cal && cal.attributes.start_time ? cal.attributes : null;
    const pdStart = pd && new Date(pd.start_time);
    const pdEnd = pd && new Date(pd.end_time);
    const dayWord = (d) => {
      const t = new Date();
      const a = new Date(d.getFullYear(), d.getMonth(), d.getDate());
      const b = new Date(t.getFullYear(), t.getMonth(), t.getDate());
      const n = Math.round((a - b) / 864e5);
      return n === 0 ? 'today' : n === 1 ? 'tomorrow' : d.toLocaleDateString(undefined, { weekday: 'long' });
    };
    const price = (v) => (admin && v != null ? ` · ${pence(v)}` : '');
    const row = (icon, col, t1, t2, pill) => `<div class="oc-crow"><div class="oc-cico" style="background:color-mix(in srgb, ${col} 22%, transparent); color:${col};">${iconHtml(icon, { size: '19px' })}</div>
      <div style="flex:1; min-width:0;"><div class="oc-ct1">${kitEsc(t1)}</div><div class="ck-sub">${kitEsc(t2)}</div></div>${pill ? `<span class="oc-pill" style="background:${col}; color:#0d2a10;">${kitEsc(pill)}</span>` : ''}</div>`;
    const cheapRow = !w
      ? row('mdi:weather-night', KIT_COLOR.off, 'No cheap rate in the next day', 'Octopus hasn\'t sent tomorrow\'s rates yet')
      : w.now
        ? row('mdi:weather-night', CHEAP, 'Cheap rate now', `Until ${hhmm(w.until)}${price(w.v)} · a good time for the washing or dishwasher`, 'Off-peak')
        : row('mdi:weather-night', KIT_COLOR.off, `Next cheap rate ${hhmm(w.from)}`, `In ${span(w.from - Date.now())}, until ${hhmm(w.until)}${price(w.v)}`);
    const pdRow = pd
      ? row('mdi:lightning-bolt', '#b39dff', `Power-down session ${dayWord(pdStart)}`, `${hhmm(pdStart)}–${hhmm(pdEnd)} · use less electricity then for Octoplus points`)
      : row('mdi:lightning-bolt', KIT_COLOR.off, 'No power-down sessions booked', 'Octopus announces them a day or so ahead');
    return {
      head: [this.config.name || 'Cheap rate', w && w.now ? 'Off-peak now' : w ? `Cheap from ${hhmm(w.from)}` : '', CHEAP],
      body: `<style>.oc-crow{display:flex;align-items:center;gap:10px;padding:6px 2px}.oc-crow+.oc-crow{border-top:1px solid var(--divider-color, rgba(127,127,127,0.18))}.oc-cico{flex:none;width:36px;height:36px;border-radius:10px;display:flex;align-items:center;justify-content:center}.oc-ct1{font-weight:600;font-size:0.92rem}</style>${cheapRow}${pdRow}`,
    };
  }

  _compactSpec(m) {
    const c = this.config;
    if (c.show === 'gas') {
      const rate = kitNum(this._s(m.gas, 'current_rate'));
      const cost = kitNum(this._s(m.gas, 'current_accumulative_cost'));
      return { name: c.name || 'Gas', color: OCTO_VIOLET, value: pence(rate), status: cost != null ? `today ${pounds(cost)}` : 'per kWh' };
    }
    if (c.show === 'octoplus') {
      const pts = kitNum(this._s(m.account, 'octoplus_points'));
      return { name: c.name || 'Octoplus', color: OCTO_PINK, value: pts == null ? '–' : String(pts), status: 'points' };
    }
    if (c.show === 'week') {
      const got = this._weekData(m);
      const w = got && !got.wk.error ? buildWeek(got.wk.result, got.ids, Date.now(), 7, this._hass.config && this._hass.config.time_zone) : null;
      return { name: c.name || 'Last 7 days', color: KIT_COLOR.comfy, value: w ? pounds(w.cost) : '–', status: w ? `${pounds(w.average)} a day` : 'last 7 days' };
    }
    const rate = kitNum(this._s(m.elec, 'current_rate'));
    const rates = this._rates(m);
    const cheapNow = rate != null && rates.cheap(rate);
    const demand = kitNum(this._s(m.elec, 'current_demand'));
    return {
      name: c.name || 'Octopus Electricity',
      color: OCTO_PINK,
      value: pence(rate),
      valueColor: cheapNow ? CHEAP : PEAK,
      status: [cheapNow ? 'Cheap' : 'Peak', demand != null ? `${Math.round(demand)} W now` : ''].filter(Boolean).join(' · '),
    };
  }

  getCardSize() {
    return 4;
  }

  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`octopus-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return { show: 'electricity' };
  }
}

kitCompactable(OctopusCard, (card) => {
  card._built = false;
  card._sig = null;
});

// ---- EV charger (myenergi Zappi).
const EV_MODES = [
  { key: 'Stopped', name: 'Stop', icon: 'mdi:stop-circle-outline', color: KIT_COLOR.off },
  { key: 'Eco', name: 'Eco', icon: 'mdi:leaf', color: KIT_COLOR.good },
  { key: 'Eco+', name: 'Eco+', icon: 'mdi:solar-power', color: KIT_COLOR.comfy },
  { key: 'Fast', name: 'Fast', icon: 'mdi:lightning-bolt', color: KIT_COLOR.poor },
];

// The Zappi's entities, from the myenergi integration.
export function evFind(hass, c = {}) {
  const ids = platformEntities(hass, 'myenergi', 'zappi').filter((id) => /zappi/.test(id));
  const st = (id) => hass.states[id];
  const pick = (dom, re) => ids.find((id) => id.startsWith(`${dom}.`) && re.test(id)) || '';
  return {
    mode: c.mode_entity || ids.find((id) => id.startsWith('select.') && (st(id).attributes.options || []).includes('Eco+')) || pick('select', /charge_mode/),
    power: c.power_entity || pick('sensor', /internal_load|charging_power|power_ct_internal/) || ids.find((id) => id.startsWith('sensor.') && st(id).attributes.device_class === 'power') || '',
    session: c.session_entity || pick('sensor', /charge_added_session|session/),
    today: c.today_entity || pick('sensor', /energy_used_today|charge_added_today/),
    status: c.status_entity || pick('sensor', /_status$/),
    plug: c.plug_entity || pick('sensor', /plug_status|plug/),
    locked: pick('binary_sensor', /_locked$/),
  };
}

export const EvChargerCardEditor = createFormEditor({
  schema: () => [
    { name: 'name', selector: { text: {} } },
    { name: 'show_buttons', selector: { boolean: {} }, default: true },
    {
      type: 'expandable',
      name: '',
      title: 'Charger entities (found automatically)',
      flatten: true,
      schema: [
        { name: 'mode_entity', selector: { entity: { domain: 'select' } } },
        { name: 'power_entity', selector: { entity: { domain: 'sensor' } } },
        { name: 'session_entity', selector: { entity: { domain: 'sensor' } } },
        { name: 'status_entity', selector: { entity: { domain: 'sensor' } } },
        { name: 'plug_entity', selector: { entity: { domain: 'sensor' } } },
      ],
    },
    { name: 'demo', selector: { boolean: {} } },
  ],
  labels: {
    name: 'Title (optional)',
    show_buttons: 'Mode buttons (Stop, Eco, Eco+, Fast)',
    mode_entity: 'Charge mode',
    power_entity: 'Charging power',
    session_entity: 'Energy added this charge',
    status_entity: 'Status',
    plug_entity: 'Plug status',
    demo: 'Show a pretend charger (for Design Presets)',
  },
  helpers: { name: 'Finds a myenergi Zappi by itself once the myenergi integration is set up.' },
});

export class EvChargerCard extends HTMLElement {
  setConfig(config) {
    this.config = config || {};
    this._built = false;
    this._sig = null;
  }

  set hass(hass) {
    this._hass = hass;
    watchIcons(this, hass);
    this._render();
  }

  _data() {
    const c = this.config;
    if (c.demo) return { found: true, mode: 'Eco+', options: ['Fast', 'Eco', 'Eco+', 'Stopped'], power: 7100, session: 18.4, status: 'Charging', plug: 'Connected', locked: false, smart: { on: true, phase: 'Waiting', later: true }, car: { role: 'Primary', confirmed: true }, boost: { id: 'switch.demo_boost', on: false }, plan: { active: false, slots: [{ start: Date.now() + 2 * 3600e3, end: Date.now() + 4 * 3600e3, kwh: 15.6, source: 'SMART' }] } };
    const e = evFind(this._hass, c);
    const s = (id) => id && this._hass.states[id];
    if (!s(e.mode) && !s(e.power) && !s(e.status)) return { found: false };
    let power = kitNum(s(e.power));
    if (power != null && s(e.power).attributes.unit_of_measurement === 'kW') power *= 1000;
    const smartOn = s('input_boolean.zappi_smart_charge');
    const smartPhase = s('input_select.zappi_smart_charge_state');
    // Which car is on the charger (Primary = the one on the Octopus app, Additional = any other); set from the plug-in question.
    const carRole = s('input_select.zappi_car_role');
    const carOk = s('input_boolean.zappi_car_confirmed');
    // Octopus's Boost ("bump charge") for the car it controls: it charges now, at the standard rate.
    const boostId = Object.keys(this._hass.states).find((id) => /^switch\.octopus_energy_.*_intelligent_bump_charge$/.test(id));
    const boostSt = boostId && s(boostId);
    return {
      found: true,
      e,
      plan: octopusPlan(this._hass),
      boost: boostSt && !['unavailable', 'unknown'].includes(boostSt.state) ? { id: boostId, on: boostSt.state === 'on' } : null,
      car: carRole ? { role: carRole.state, confirmed: carOk ? carOk.state === 'on' : true } : null,
      smart: smartOn ? { on: smartOn.state === 'on', phase: smartPhase ? smartPhase.state : null, later: !!s('script.zappi_charge_later') } : null,
      mode: s(e.mode) ? s(e.mode).state : null,
      options: s(e.mode) ? s(e.mode).attributes.options || [] : [],
      power,
      session: kitNum(s(e.session)),
      status: s(e.status) ? s(e.status).state : null,
      plug: s(e.plug) ? s(e.plug).state : null,
      locked: s(e.locked) ? s(e.locked).state === 'on' : null,
      unavailable: [e.mode, e.power, e.status].filter(Boolean).every((id) => !s(id) || s(id).state === 'unavailable'),
    };
  }

  // The Octopus rate right now (for "charging at 5.0p").
  _rate() {
    const m = octoFind(this._hass);
    const r = m.elec && this._hass.states[`sensor.${m.elec}current_rate`];
    return kitNum(r);
  }

  _render() {
    if (!this._hass) return;
    const c = this.config;
    const d = this._data();
    const charging = d.found && d.power != null && d.power > 100;
    // Stop was picked but the charger's readings haven't caught up yet (they refresh about once a minute).
    const stopping = d.found && !d.unavailable && d.mode === 'Stopped' && (charging || /^charging/i.test(d.plug || ''));
    const col = !d.found || d.unavailable ? KIT_COLOR.off : charging ? EV_TEAL : KIT_COLOR.off;
    const word = !d.found ? 'Not connected yet' : d.unavailable ? 'Unavailable' : stopping ? 'Stopping…' : charging ? `Charging · ${d.mode || ''}`.replace(/ · $/, '') : kitCap(d.status || d.plug || d.mode || 'Idle');
    if (this._compact) {
      return kitCompact(this, {
        name: c.name || 'Car charger',
        color: col,
        value: charging ? `${(d.power / 1000).toFixed(1)} kW` : '',
        valueColor: EV_TEAL,
        status: d.found ? word : 'Not connected yet',
        // While a car's plugged in, the overrides take the modes' place (tap the name for the modes).
        buttons: [
          ...(this._compactActs(d, charging) || (d.found && c.show_buttons !== false && d.mode != null ? EV_MODES.filter((b) => !d.options.length || d.options.includes(b.key)).map((b) => ({ ...b, icon: iconFor('charger', b.key, b.icon), label: b.name, on: d.mode === b.key })) : [])),
          ...(d.found && !d.unavailable && d.smart ? [{ key: 'smart', act: 'smart', icon: 'mdi:clock-fast', label: 'Smart', on: d.smart.on, color: EV_TEAL }] : []),
        ],
        onButton: (b) => (b.act ? this._override(b.act) : this._pick(b.key)),
      });
    }
    if (!this._built) {
      this.innerHTML = kitShell(`<div class="ev-body" style="display:flex; flex-direction:column; gap:10px;"></div><div class="ev-acts"></div><div class="ck-row ev-modes"></div>`, `
        .ev-acts { display:grid; grid-template-columns:1fr; gap:8px; }
        .ev-acts:empty { display:none; }
        .ev-act { border:none; cursor:pointer; font:inherit; font-size:.85rem; font-weight:600; border-radius:12px; min-height:44px; padding:8px 12px;
          display:flex; align-items:center; justify-content:center; gap:8px; background:rgba(127,127,127,.14); color:var(--primary-text-color); }
        .ev-act.go { background:color-mix(in srgb, ${EV_TEAL} 24%, transparent); }
        .ev-act:disabled { cursor:default; opacity:.75; }
        .ev-act.none { color:var(--secondary-text-color); }
        .ev-act.live { background:color-mix(in srgb, ${EV_TEAL} 32%, transparent); color:${EV_TEAL}; opacity:1; }
        .ev-act.stopping { background:rgba(127,127,127,.2); color:var(--secondary-text-color); opacity:1; }
        .ev-act.smart { justify-content:flex-start; text-align:left; min-height:52px; }
        .ev-plan { border-radius:12px; background:rgba(127,127,127,.12); padding:10px 12px; display:flex; flex-direction:column; gap:3px; }
        .ev-plan b { font-size:.85rem; font-weight:600; }
        .ev-plan span { font-size:.78rem; color:var(--secondary-text-color); font-variant-numeric:tabular-nums; }
        .ev-plan span.now { color:${EV_TEAL}; font-weight:600; }
        .ev-act.car { justify-content:flex-start; text-align:left; min-height:52px; cursor:default; }
        .ev-act.car .ev-st { flex:1; display:flex; flex-direction:column; gap:1px; min-width:0; }
        .ev-act.car .ev-st small { font-weight:400; font-size:.72rem; color:var(--secondary-text-color); white-space:normal; line-height:1.25; }
        .ev-sel { flex:none; font:inherit; font-size:.85rem; font-weight:600; color:var(--primary-text-color); background:rgba(127,127,127,.18); border:none; border-radius:10px; min-height:36px; padding:4px 8px; }
        .ev-act.smart .ev-st { flex:1; display:flex; flex-direction:column; gap:1px; min-width:0; }
        .ev-act.smart .ev-st small { font-weight:400; font-size:.72rem; color:var(--secondary-text-color); white-space:normal; line-height:1.25; }
        .ev-sw { flex:none; width:38px; height:22px; border-radius:999px; background:rgba(127,127,127,.45); position:relative; transition:background .15s; }
        .ev-sw::after { content:''; position:absolute; top:3px; left:3px; width:16px; height:16px; border-radius:50%; background:#fff; transition:left .15s; }
        .ev-sw.on { background:${EV_TEAL}; }
        .ev-sw.on::after { left:19px; }
        .ev-big { display:flex; align-items:baseline; gap:10px; }
        .ev-big b { font-size:2.2rem; font-weight:300; font-variant-numeric:tabular-nums; line-height:1.1; }
        .ev-two { display:grid; grid-template-columns:1fr 1fr; gap:8px; }
        .ev-stat { border-radius:12px; background:rgba(127,127,127,.12); padding:10px 12px; display:flex; flex-direction:column; gap:2px; min-width:0; }
        .ev-stat b { font-size:1.15rem; font-weight:600; font-variant-numeric:tabular-nums; }
        .ev-stat span { font-size:.72rem; color:var(--secondary-text-color); }`);
      this._body = this.querySelector('.ev-body');
      this._acts = this.querySelector('.ev-acts');
      this._acts.addEventListener('click', (ev) => {
        const b = ev.target.closest('[data-act]');
        if (b && !b.disabled) this._override(b.dataset.act);
        if (ev.target.closest('[data-smart]')) this._override('smart');
        if (ev.target.closest('[data-boost]')) this._boost();
      });
      this._acts.addEventListener('change', (ev) => {
        const sel = ev.target.closest('[data-car]');
        if (sel && sel.value) this._setCar(sel.value);
      });
      this._built = true;
    }
    kitHead(this, c.name || 'Car charger', word + (c.demo ? ' · demo' : ''), col === KIT_COLOR.off ? EV_TEAL : col);
    const rate = this._rate();
    const sig = JSON.stringify([d, rate, stopping, d.smart ? this._smartText(d) : '']);
    if (sig !== this._sig) {
      this._sig = sig;
      if (!d.found) {
        this._body.innerHTML = `<div style="display:flex; gap:10px; align-items:center;">${iconHtml('mdi:ev-station', { size: '28px', style: `color:${EV_TEAL}; flex:none;` })}<div class="ck-sub" style="line-height:1.5;">Not connected yet. Once the myenergi integration is set up (hub serial and API key), this card finds the Zappi by itself.</div></div>`;
      } else {
        const cheap = rate != null && rate < 0.1;
        this._body.innerHTML = `
          <div class="ev-big"><b style="color:${charging ? EV_TEAL : 'var(--secondary-text-color)'};">${charging ? `${(d.power / 1000).toFixed(1)} kW` : 'Not charging'}</b>${charging ? `<span class="ck-sub">${stopping ? 'stopping…' : 'charging now'}</span>` : ''}${rate != null ? `<span style="margin-left:auto; font-size:.72rem; font-weight:700; padding:2px 9px; border-radius:999px; background:color-mix(in srgb, ${cheap ? CHEAP : PEAK} 22%, transparent); color:${cheap ? CHEAP : PEAK};">${pence(rate)} rate</span>` : ''}</div>
          <div class="ev-two">
            <div class="ev-stat"><b>${d.session == null ? '–' : `${d.session.toFixed(1)} kWh`}</b><span>This charge${d.session != null && rate != null && charging ? ` · about ${pounds(d.session * rate)}` : ''}</span></div>
            <div class="ev-stat"><b>${kitEsc(kitCap(d.plug || d.status || '–'))}</b><span>${d.plug ? 'Plug' : 'Status'}</span></div>
          </div>`;
      }
    }
    this._renderActs(d, charging);
    const modes = d.found && c.show_buttons !== false && d.mode != null ? EV_MODES.filter((b) => !d.options.length || d.options.includes(b.key)).map((b) => ({ ...b, icon: iconFor('charger', b.key, b.icon), on: d.mode === b.key })) : [];
    kitTiles(this.querySelector('.ev-modes'), modes, (t) => this._pick(t.key));
    hydrateIcons(this);
  }

  _setMode(mode) {
    if (this.config.demo) return;
    const e = evFind(this._hass, this.config);
    if (e.mode) this._hass.callService('select', 'select_option', { entity_id: e.mode, option: mode });
  }

  // One button that's also the status: "No car connected" (greyed), "Start charge" (unlock if locked,
  // then Fast) until it's charging, then a "Charging" marker (compact view: modes instead when no car). The charger's lock
  // sensor stays on even mid-charge, so it isn't shown. Stop is the mode tile.
  _actState(d, charging) {
    const plugged = d.found && !d.unavailable && d.plug && !/disconnected/i.test(d.plug);
    // The Zappi flips to "Waiting for EV" with 0 W between bursts; its plug status still says Charging.
    const active = charging || /^charging/i.test(d.plug || '');
    if (!plugged && !active) return { none: true };
    const pend = this._pend && Date.now() - this._pend.at < 60000 && this._pend.act === 'charge';
    if (active && d.mode === 'Stopped') return { charging: true, stopping: true };
    return active ? { charging: true } : { charging: false, pend };
  }

  _renderActs(d, charging) {
    if (!this._acts) return;
    const a = this._actState(d, charging);
    const html = !a || d.unavailable || !d.found
      ? ''
      : a.none
        ? `<button type="button" class="ev-act none" disabled aria-label="No car connected">${iconHtml('mdi:ev-plug-type2', { size: '18px' })}No car connected</button>`
        : a.stopping
        ? `<button type="button" class="ev-act stopping" disabled aria-label="Stopping">${iconHtml('mdi:stop-circle-outline', { size: '18px' })}Stopping…</button>`
        : a.charging
        ? `<button type="button" class="ev-act live" disabled aria-label="Charging">${iconHtml('mdi:lightning-bolt', { size: '18px' })}Charging</button>`
        : `<button type="button" class="ev-act go" data-act="charge"${a.pend ? ' disabled' : ''}>${iconHtml('mdi:lightning-bolt', { size: '18px' })}${a.pend ? 'Starting…' : 'Start charge'}</button>`;
    const sm = d.smart;
    const smart = sm && d.found && !d.unavailable
      ? `<button type="button" class="ev-act smart" data-smart role="switch" aria-checked="${sm.on}" aria-label="Smart charge">${iconHtml('mdi:clock-fast', { size: '20px' })}<span class="ev-st">Smart charge<small>${kitEsc(this._smartText(d))}</small></span><span class="ev-sw${sm.on ? ' on' : ''}"></span></button>`
      : '';
    // Which car is plugged in: only while one is. A car nobody has answered for shows "Which car?" and picking one confirms it.
    const plugged = !a || !a.none;
    const cr = d.car;
    const car = cr && plugged && d.found && !d.unavailable
      ? `<label class="ev-act car">${iconHtml('mdi:car-electric', { size: '20px' })}<span class="ev-st">Car plugged in<small>${kitEsc(cr.role === 'Primary' ? 'Primary: also charges when Octopus schedules it' : cr.role === 'Additional' ? 'Additional: charges 23:30 to 05:30 only' : 'Not answered yet, so it will not charge')}</small></span><select class="ev-sel" data-car aria-label="Car plugged in">${cr.role === 'Unknown' || !cr.confirmed ? `<option value=""${cr.role === 'Unknown' ? ' selected' : ''} disabled>Which car?</option>` : ''}<option value="Primary"${cr.role === 'Primary' ? ' selected' : ''}>Primary</option><option value="Additional"${cr.role === 'Additional' ? ' selected' : ''}>Additional</option></select></label>`
      : '';
    // Boost (Octopus): only for the Primary car (Octopus controls no other), while plugged in. It is billed at the standard rate, so say so.
    const bst = d.boost && cr && cr.role === 'Primary' && plugged && d.found && !d.unavailable
      ? `<button type="button" class="ev-act smart" data-boost role="switch" aria-checked="${d.boost.on}" aria-label="Boost charge">${iconHtml('mdi:rocket-launch', { size: '20px' })}<span class="ev-st">Boost (Octopus)<small>${d.boost.on ? 'On: Octopus is charging now, at the standard rate' : 'Charge now, at the standard rate, not the cheap rate'}</small></span><span class="ev-sw${d.boost.on ? ' on' : ''}"></span></button>`
      : '';
    // Octopus's charging plan for the car it controls (also on the Energy page, which shows this same card).
    const pl = d.plan && d.found && !d.unavailable
      ? `<div class="ev-plan"><b>Octopus charging plan</b>${
          d.plan.slots.length
            ? d.plan.slots.slice(0, 4).map((x) => `<span${x.start <= Date.now() ? ' class="now"' : ''}>${kitEsc(planLine(x))}</span>`).join('')
            : '<span>Nothing planned yet</span>'
        }</div>`
      : '';
    const all = html + smart + pl + car + bst;
    if (all !== this._actsHtml) {
      this._actsHtml = all;
      this._acts.innerHTML = all;
      hydrateIcons(this._acts);
    }
  }

  // The override as a compact button, or null when no car's plugged in.
  _compactActs(d, charging) {
    const a = d.mode == null ? null : this._actState(d, charging);
    if (!a || a.none) return null;
    return [a.stopping
      ? { key: 'stopping', act: 'none', icon: 'mdi:stop-circle-outline', label: 'Stopping…', on: true, color: EV_TEAL }
      : a.charging
      ? { key: 'charging', act: 'none', icon: 'mdi:lightning-bolt', label: 'Charging', on: true, color: EV_TEAL }
      : { key: 'charge', act: 'charge', icon: 'mdi:lightning-bolt', label: a.pend ? 'Starting…' : 'Start charge', on: true, color: EV_TEAL }];
  }

  // The cheap rate window as { now, from, until, v }, or null when Octopus hasn't sent rates.
  _cheapWindow() {
    if (this.config.demo) return { now: false, from: new Date(new Date().setHours(24, 30, 0, 0)), until: new Date(new Date().setHours(29, 30, 0, 0)), v: 0.0476 };
    return windowOf(ratesOf(this._hass, octoFind(this._hass)));
  }

  // What the Smart charge switch says under its name.
  _smartText(d) {
    const sm = d.smart;
    if (!sm.on) return 'Off: it only charges when you start it';
    const w = this._cheapWindow();
    if (sm.phase === 'Charging') return w && w.now ? `Charging at the cheap rate until ${hhmm(w.until)}` : 'Charging at the cheap rate';
    if (sm.phase === 'Waiting') return w && !w.now ? `Waiting for the cheap rate, ${hhmm(w.from)}` : 'Starting at the cheap rate';
    if (sm.phase === 'Done') return 'Finished for this plug-in';
    if (sm.phase === 'Manual') return "You're in control for this plug-in";
    return 'Charges at the cheap rate when a car is plugged in';
  }

  // Start charge / Fast pressed at the normal rate with the cheap rate coming up: ask first.
  // Returns true when it asked (the choice carries on from the pop-up).
  _ask(kind) {
    const d = this._data();
    if (!d.found || !d.smart || !d.smart.later) return false;
    const w = this._cheapWindow();
    if (!w || w.now || w.from - Date.now() > 18 * 3600e3) return false;
    const rate = this.config.demo ? 0.253 : this._rate();
    const at = hhmm(w.from);
    const box = document.createElement('div');
    box.style.cssText = 'display:flex; flex-direction:column; gap:10px; padding:2px 2px 10px;';
    box.innerHTML = `<div style="line-height:1.5; font-size:.92rem;">It's the normal rate now${rate != null ? ` (<b>${pence(rate)}</b> a kWh)` : ''}. The cheap rate (<b>${pence(w.v)}</b>) starts at <b>${at}</b>, in ${span(w.from - Date.now())}.</div>
      <button type="button" class="ev-act go" data-later>${iconHtml('mdi:clock-fast', { size: '18px' })}Charge at ${at} (cheap rate)</button>
      <button type="button" class="ev-act" data-now>${iconHtml('mdi:lightning-bolt', { size: '18px' })}Charge now${rate != null ? ` at ${pence(rate)}` : ''}</button>
      <div class="ck-sub" style="line-height:1.4;">Charging later hands it to smart charge: it starts by itself at the cheap rate and stops when that ends.</div>`;
    const pop = openPopup(this, { title: 'Wait for the cheap rate?', icon: 'mdi:clock-fast', color: EV_TEAL, content: box });
    box.querySelector('[data-later]').addEventListener('click', () => {
      if (!this.config.demo) this._hass.callService('script', 'turn_on', {}, { entity_id: 'script.zappi_charge_later' }).catch(() => {});
      pop.close();
    });
    box.querySelector('[data-now]').addEventListener('click', () => {
      pop.close();
      if (kind === 'fast') this._setMode('Fast');
      else this._start();
    });
    return true;
  }

  // A mode tile: Fast at the normal rate is asked about first.
  _pick(mode) {
    if (mode === 'Fast' && this._ask('fast')) return;
    this._setMode(mode);
  }

  // Set which car is plugged in. An unanswered car is confirmed by the choice (same as the notification buttons);
  // a confirmed one only changes its role, so a charge someone took over by hand carries on.
  _setCar(role) {
    if (this.config.demo || (role !== 'Primary' && role !== 'Additional')) return;
    const d = this._data();
    if (!d.car) return;
    if (d.car.confirmed) this._hass.callService('input_select', 'select_option', { option: role }, { entity_id: 'input_select.zappi_car_role' }).catch(() => {});
    else this._hass.callService('script', 'zappi_car_yes', { role }).catch(() => {});
  }

  // Octopus's Boost on or off for the car it controls.
  _boost() {
    if (this.config.demo) return;
    const d = this._data();
    if (!d.boost) return;
    this._hass.callService('switch', d.boost.on ? 'turn_off' : 'turn_on', {}, { entity_id: d.boost.id }).catch(() => {});
  }

  _override(act) {
    if (act === 'smart') {
      const d = this._data();
      if (!this.config.demo && d.smart) this._hass.callService('input_boolean', d.smart.on ? 'turn_off' : 'turn_on', {}, { entity_id: 'input_boolean.zappi_smart_charge' }).catch(() => {});
      return;
    }
    if (act !== 'charge') return;
    if (this._ask('charge')) return;
    this._start();
  }

  // Start charge: unlock if locked, then Fast.
  _start() {
    if (this.config.demo) return;
    const e = evFind(this._hass, this.config);
    if (!e.mode) return;
    const locked = e.locked && this._hass.states[e.locked] && this._hass.states[e.locked].state === 'on';
    const unlock = () => this._hass.callService('myenergi', 'myenergi_unlock', {}, { entity_id: e.mode }).catch(() => {});
    // Unlock first when locked, then Fast (the charger otherwise waits at the unit).
    (locked ? unlock() : Promise.resolve()).then(() => this._setMode('Fast'));
    this._pend = { act: 'charge', at: Date.now() };
    this._actsHtml = null;
    this._sig = null;
    this._render();
    // Clear "Starting…" if nothing's changed after a minute.
    clearTimeout(this._pendTimer);
    this._pendTimer = setTimeout(() => { this._pend = null; this._actsHtml = null; this._sig = null; this._render(); }, 60500);
  }

  getCardSize() {
    return 4;
  }

  getGridOptions() {
    return { columns: 12, min_columns: 6, rows: 'auto' };
  }

  static getConfigElement() {
    return document.createElement(`ev-charger-card-editor${SUFFIX}`);
  }

  static getStubConfig() {
    return {};
  }
}

kitCompactable(EvChargerCard, (card) => {
  card._built = false;
  card._sig = null;
});

export function registerEnergyCards() {
  [
    ['octopus-card', OctopusCard, OctopusCardEditor, 'Octopus Energy Card', 'Octopus rates, live use, last full day, gas and Octoplus'],
    ['ev-charger-card', EvChargerCard, EvChargerCardEditor, 'EV Charger Card', 'A myenergi Zappi: charging, this charge and mode buttons'],
  ].forEach(([name, Cls, Ed, label, description]) => {
    if (!customElements.get(`${name}-editor${SUFFIX}`)) customElements.define(`${name}-editor${SUFFIX}`, Ed);
    if (!customElements.get(`${name}${SUFFIX}`)) customElements.define(`${name}${SUFFIX}`, Cls);
    window.customCards = window.customCards || [];
    window.customCards.push({ type: `${name}${SUFFIX}`, name: `${label}${LABEL}`, description, preview: true, documentationURL: 'https://github.com/J45PER/church-drive-cards#readme' });
  });
}
