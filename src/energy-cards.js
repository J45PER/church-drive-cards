// Energy cards for Octopus Energy (the BottlecapDave integration) and a
// myenergi Zappi car charger. Both find their entities by themselves.
//
// Octopus Card, one of four views (`show`):
// - electricity: the rate this half-hour (Cheap or Peak), live use from a
//   Home Mini, today's running cost, and a 24-hour strip of today's rates
//   with the cheap window and how long until it starts;
// - last_day: the latest complete day Octopus has sent (a day or two late):
//   cost of electricity and gas, half-hour use coloured by rate and how much
//   ran at the cheap rate;
// - gas: the rate, standing charge and today so far;
// - octoplus: points, weekend happy hours and saving sessions;
// - cheap: a strip for everyone: cheap rate now (until when) or when it next
//   starts, and the next Octoplus power-down session (prices for admins only).
//
// EV Charger Card: a Zappi's state, power, this charge and mode buttons
// (Stop, Eco, Eco+, Fast). Until the myenergi integration is set up it says
// so and waits.

import { createFormEditor } from './form-editor.js';
import { iconHtml, hydrateIcons } from './icons.js';
import { SUFFIX, LABEL } from './suffix.js';
import { KIT_COLOR, kitShell, kitHead, kitTiles, kitNum, kitCap, kitEsc, kitCompact, kitCompactable } from './card-kit.js';

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
  { value: 'last_day', label: 'Last full day (electricity and gas)' },
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
    this.config = { show: 'electricity', ...config };
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
    const get = (k) => (this._s(m.elec, k, 'event') && this._s(m.elec, k, 'event').attributes.rates) || [];
    const all = [...get('current_day_rates'), ...get('next_day_rates')]
      .map((r) => ({ start: new Date(r.start), end: new Date(r.end), v: Number(r.value_inc_vat) }))
      .filter((r) => !isNaN(r.start) && !isNaN(r.v))
      .sort((a, b) => a.start - b.start);
    const min = all.length ? Math.min(...all.map((r) => r.v)) : null;
    const max = all.length ? Math.max(...all.map((r) => r.v)) : null;
    const cheap = (v) => min != null && max != null && max - min > 0.001 && v <= min + 0.001;
    return { all, cheap, min };
  }

  // "Cheap now until 05:30" or the next cheap window and how long until it.
  _window(rates) {
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
        .oc-pill { margin-left:auto; font-size:.72rem; font-weight:700; padding:2px 9px; border-radius:999px; }`);
      this._body = this.querySelector('.oc-body');
      this._built = true;
    }
    const minute = Math.floor(Date.now() / 60e3);
    const html = view === 'last_day' ? this._lastDay(m) : view === 'gas' ? this._gas(m) : view === 'octoplus' ? this._octoplus(m) : view === 'cheap' ? this._cheap(m) : this._elec(m);
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

  _lastDay(m) {
    if (!m.elec) return this._missing('electricity meter');
    const cost = this._s(m.elec, 'previous_accumulative_cost');
    const charges = (cost && cost.attributes.charges) || [];
    if (!charges.length) return { head: [this.config.name || 'Last full day', 'Waiting for Octopus', KIT_COLOR.off], body: '<div class="ck-sub">Octopus hasn\'t sent a full day of readings yet.</div>' };
    const day = new Date(charges[0].start);
    const rates = charges.map((x) => Number(x.rate));
    const lo = Math.min(...rates), hi = Math.max(...rates);
    const isCheap = (r) => hi - lo > 0.001 && r <= lo + 0.001;
    const kwh = charges.reduce((s, x) => s + Number(x.consumption || 0), 0);
    const cheapKwh = charges.filter((x) => isCheap(Number(x.rate))).reduce((s, x) => s + Number(x.consumption || 0), 0);
    const share = kwh ? Math.round((cheapKwh / kwh) * 100) : 0;
    const eTotal = Number(cost.attributes.total != null ? cost.attributes.total : cost.state);
    const gasCost = this._s(m.gas, 'previous_accumulative_cost');
    const gasKwh = kitNum(this._s(m.gas, 'previous_accumulative_consumption_kwh'));
    const gTotal = gasCost ? Number(gasCost.attributes.total != null ? gasCost.attributes.total : gasCost.state) : null;
    const max = Math.max(...charges.map((x) => Number(x.consumption || 0)), 0.01);
    const bars = charges.map((x) => `<i title="${kitEsc(hhmm(new Date(x.start)))} · ${Number(x.consumption).toFixed(2)} kWh · ${pence(Number(x.rate))}" style="height:${(Number(x.consumption || 0) / max) * 100}%; background:${isCheap(Number(x.rate)) ? CHEAP : PEAK};"></i>`).join('');
    const saving = hi - lo > 0.001 ? `Each kWh moved to the cheap rate saves ${pence(hi - lo)}.` : '';
    const dayName = day.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
    return {
      head: [this.config.name || 'Last full day', `${dayName} · ${pounds(eTotal + (gTotal || 0))}`, KIT_COLOR.comfy],
      body: `
        <div class="oc-two">
          <div class="oc-stat"><b>${pounds(eTotal)}</b><span>Electricity · ${kwh.toFixed(1)} kWh</span></div>
          <div class="oc-stat"><b>${pounds(gTotal)}</b><span>Gas${gasKwh != null ? ` · ${gasKwh.toFixed(1)} kWh` : ''}</span></div>
        </div>
        <div class="oc-bars">${bars}</div>
        <div class="oc-ticks"><span>00:00</span><span>06:00</span><span>12:00</span><span>18:00</span><span>24:00</span></div>
        ${hi - lo > 0.001 ? `<div class="oc-meter"><i style="width:${share}%; background:${CHEAP};"></i><i style="width:${100 - share}%; background:${PEAK};"></i></div>
        <div class="ck-sub"><b style="color:${CHEAP};">${share}%</b> of electricity was used at the cheap rate. ${saving}</div>` : ''}
        <div class="ck-sub">Octopus sends readings a day or two late. Costs include standing charges.</div>`,
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
    if (c.show === 'last_day') {
      const cost = this._s(m.elec, 'previous_accumulative_cost');
      const gas = this._s(m.gas, 'previous_accumulative_cost');
      const total = (cost ? Number(cost.attributes.total != null ? cost.attributes.total : cost.state) : 0) + (gas ? Number(gas.attributes.total != null ? gas.attributes.total : gas.state) : 0);
      return { name: c.name || 'Last full day', color: KIT_COLOR.comfy, value: pounds(total), status: 'electricity and gas' };
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
  };
}

// Cars from their own integrations (Vauxhall/Peugeot/… via Stellantis Vehicles,
// VW group via VW Group Connect): one per device, with its battery %, range and
// whether it's plugged in / charging. Found by themselves; `cars` overrides.
const CAR_PLATFORMS = ['stellantis_vehicles', 'vag_connect', 'volkswagencarnet', 'volkswagen_we_connect_id'];

export function carsFind(hass, c = {}) {
  if (c.show_cars === false || !hass) return [];
  const st = (id) => (id && hass.states[id]) || null;
  if (Array.isArray(c.cars) && c.cars.length)
    return c.cars.map((x) => ({ name: x.name || '', battery: x.battery || '', range: x.range || '', plugged: x.plugged || '', charging: x.charging || '' })).filter((x) => st(x.battery));
  const reg = hass.entities || {};
  const devs = {};
  Object.values(reg).forEach((e) => {
    if (!CAR_PLATFORMS.includes(e.platform) || !e.device_id || e.hidden || e.disabled_by) return;
    (devs[e.device_id] = devs[e.device_id] || []).push(e.entity_id);
  });
  return Object.entries(devs)
    .map(([dev, ids]) => {
      const pick = (dom, re, skip) => ids.find((id) => id.startsWith(`${dom}.`) && re.test(id) && !(skip && skip.test(id)) && st(id)) || '';
      const battery =
        ids.find((id) => id.startsWith('sensor.') && st(id) && st(id).attributes.device_class === 'battery' && st(id).attributes.unit_of_measurement === '%' && !/_(service|12v|aux|soh|key)/i.test(id)) || '';
      const d = (hass.devices || {})[dev] || {};
      return {
        name: d.name_by_user || d.name || '',
        battery,
        range: pick('sensor', /(electric_|battery_)?range$/, /fuel|combustion|total/),
        plugged: pick('binary_sensor', /plug/, /lock/),
        charging: pick('binary_sensor', /charging$/),
      };
    })
    .filter((x) => x.battery);
}

export const EvChargerCardEditor = createFormEditor({
  schema: () => [
    { name: 'name', selector: { text: {} } },
    { name: 'show_buttons', selector: { boolean: {} }, default: true },
    { name: 'show_cars', selector: { boolean: {} }, default: true },
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
    show_cars: "Cars' battery (found from their own integrations)",
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
    this._render();
  }

  _data() {
    const c = this.config;
    if (c.demo)
      return {
        found: true, mode: 'Eco+', options: ['Fast', 'Eco', 'Eco+', 'Stopped'], power: 7100, session: 18.4, status: 'Charging', plug: 'Connected',
        cars: c.show_cars === false ? [] : [{ name: 'Electric car', battery: 64, range: '142 mi', plugged: true, charging: true }, { name: 'Hybrid', battery: 100, range: '28 mi', plugged: false, charging: false }],
      };
    const e = evFind(this._hass, c);
    const s = (id) => id && this._hass.states[id];
    const cars = carsFind(this._hass, c).map((x) => {
      const r = s(x.range);
      return {
        name: x.name,
        battery: kitNum(s(x.battery)),
        range: r && kitNum(r) != null ? `${Math.round(kitNum(r))} ${r.attributes.unit_of_measurement || ''}`.trim() : '',
        plugged: !!(s(x.plugged) && s(x.plugged).state === 'on'),
        charging: !!(s(x.charging) && s(x.charging).state === 'on'),
      };
    });
    if (!s(e.mode) && !s(e.power) && !s(e.status)) return { found: false, cars };
    let power = kitNum(s(e.power));
    if (power != null && s(e.power).attributes.unit_of_measurement === 'kW') power *= 1000;
    return {
      found: true,
      e,
      mode: s(e.mode) ? s(e.mode).state : null,
      options: s(e.mode) ? s(e.mode).attributes.options || [] : [],
      power,
      session: kitNum(s(e.session)),
      status: s(e.status) ? s(e.status).state : null,
      plug: s(e.plug) ? s(e.plug).state : null,
      unavailable: [e.mode, e.power, e.status].filter(Boolean).every((id) => !s(id) || s(id).state === 'unavailable'),
      cars,
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
    const col = !d.found || d.unavailable ? KIT_COLOR.off : charging ? EV_TEAL : KIT_COLOR.off;
    const word = !d.found ? 'Not connected yet' : d.unavailable ? 'Unavailable' : charging ? `Charging · ${d.mode || ''}`.replace(/ · $/, '') : kitCap(d.status || d.plug || d.mode || 'Idle');
    if (this._compact) {
      return kitCompact(this, {
        name: c.name || 'Car charger',
        color: col,
        value: charging ? `${(d.power / 1000).toFixed(1)} kW` : '',
        valueColor: EV_TEAL,
        status: (d.found ? word : 'Not connected yet') + this._carsShort(d),
        buttons: d.found && c.show_buttons !== false && d.mode != null ? EV_MODES.filter((b) => !d.options.length || d.options.includes(b.key)).map((b) => ({ ...b, label: b.name, on: d.mode === b.key })) : [],
        onButton: (b) => this._setMode(b.key),
      });
    }
    if (!this._built) {
      this.innerHTML = kitShell(`<div class="ev-body" style="display:flex; flex-direction:column; gap:10px;"></div><div class="ck-row ev-modes"></div>`, `
        .ev-big { display:flex; align-items:baseline; gap:10px; }
        .ev-big b { font-size:2.2rem; font-weight:300; font-variant-numeric:tabular-nums; line-height:1.1; }
        .ev-two { display:grid; grid-template-columns:1fr 1fr; gap:8px; }
        .ev-stat { border-radius:12px; background:rgba(127,127,127,.12); padding:10px 12px; display:flex; flex-direction:column; gap:2px; min-width:0; }
        .ev-stat b { font-size:1.15rem; font-weight:600; font-variant-numeric:tabular-nums; }
        .ev-stat span { font-size:.72rem; color:var(--secondary-text-color); }
        .ev-cars { display:flex; flex-direction:column; gap:8px; }
        .ev-car { display:grid; grid-template-columns:auto 1fr auto; align-items:center; gap:10px; }
        .ev-car-name { font-size:.85rem; font-weight:600; display:flex; gap:6px; align-items:center; min-width:0; }
        .ev-car-name em { font-style:normal; font-size:.68rem; font-weight:700; padding:1px 7px; border-radius:999px; white-space:nowrap; }
        .ev-bar { height:6px; border-radius:3px; background:rgba(127,127,127,.2); overflow:hidden; margin-top:4px; }
        .ev-bar i { display:block; height:100%; border-radius:3px; }
        .ev-car-pc { text-align:right; font-variant-numeric:tabular-nums; }
        .ev-car-pc b { font-size:1.05rem; font-weight:600; }
        .ev-car-pc span { display:block; font-size:.7rem; color:var(--secondary-text-color); }`);
      this._body = this.querySelector('.ev-body');
      this._built = true;
    }
    kitHead(this, c.name || 'Car charger', word + (c.demo ? ' · demo' : ''), col === KIT_COLOR.off ? EV_TEAL : col);
    const rate = this._rate();
    const sig = JSON.stringify([d, rate]);
    if (sig !== this._sig) {
      this._sig = sig;
      if (!d.found) {
        this._body.innerHTML = `<div style="display:flex; gap:10px; align-items:center;">${iconHtml('mdi:ev-station', { size: '28px', style: `color:${EV_TEAL}; flex:none;` })}<div class="ck-sub" style="line-height:1.5;">Not connected yet. Once the myenergi integration is set up (hub serial and API key), this card finds the Zappi by itself.</div></div>${this._carsHtml(d)}`;
      } else {
        const cheap = rate != null && rate < 0.1;
        this._body.innerHTML = `
          <div class="ev-big"><b style="color:${charging ? EV_TEAL : 'var(--secondary-text-color)'};">${charging ? `${(d.power / 1000).toFixed(1)} kW` : 'Not charging'}</b>${charging ? '<span class="ck-sub">charging now</span>' : ''}${rate != null ? `<span style="margin-left:auto; font-size:.72rem; font-weight:700; padding:2px 9px; border-radius:999px; background:color-mix(in srgb, ${cheap ? CHEAP : PEAK} 22%, transparent); color:${cheap ? CHEAP : PEAK};">${pence(rate)} rate</span>` : ''}</div>
          <div class="ev-two">
            <div class="ev-stat"><b>${d.session == null ? '–' : `${d.session.toFixed(1)} kWh`}</b><span>This charge${d.session != null && rate != null && charging ? ` · about ${pounds(d.session * rate)}` : ''}</span></div>
            <div class="ev-stat"><b>${kitEsc(kitCap(d.plug || d.status || '–'))}</b><span>${d.plug ? 'Plug' : 'Status'}</span></div>
          </div>${this._carsHtml(d)}`;
      }
    }
    const modes = d.found && c.show_buttons !== false && d.mode != null ? EV_MODES.filter((b) => !d.options.length || d.options.includes(b.key)).map((b) => ({ ...b, on: d.mode === b.key })) : [];
    kitTiles(this.querySelector('.ev-modes'), modes, (t) => this._setMode(t.key));
    hydrateIcons(this);
  }

  // One row per car: name (with Plugged in / Charging), battery bar, % and range.
  _carsHtml(d) {
    const cars = d.cars || [];
    if (!cars.length) return '';
    const barCol = (p) => (p == null ? KIT_COLOR.off : p < 20 ? '#ef5350' : p < 40 ? '#ffa726' : EV_TEAL);
    return `<div class="ev-cars">${cars
      .map((x) => {
        const tag = x.charging ? ['Charging', EV_TEAL] : x.plugged ? ['Plugged in', '#42a5f5'] : null;
        return `<div class="ev-car">${iconHtml(x.charging ? 'mdi:car-electric' : 'mdi:car', { size: '22px', style: `color:${x.plugged || x.charging ? EV_TEAL : 'var(--secondary-text-color)'};` })}
          <div style="min-width:0;"><div class="ev-car-name"><span style="overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${kitEsc(x.name || 'Car')}</span>${tag ? `<em style="background:color-mix(in srgb, ${tag[1]} 22%, transparent); color:${tag[1]};">${tag[0]}</em>` : ''}</div>
          <div class="ev-bar"><i style="width:${Math.max(0, Math.min(100, x.battery || 0))}%; background:${barCol(x.battery)};"></i></div></div>
          <div class="ev-car-pc"><b>${x.battery == null ? '–' : `${Math.round(x.battery)}%`}</b>${x.range ? `<span>${kitEsc(x.range)}</span>` : ''}</div></div>`;
      })
      .join('')}</div>`;
  }

  // For the compact row: the plugged-in car's battery, e.g. " · Astra 67%".
  _carsShort(d) {
    const x = (d.cars || []).find((y) => y.plugged || y.charging);
    return x && x.battery != null ? ` · ${(x.name || 'Car').replace(/^(vauxhall|volkswagen|vw)\s+/i, '')} ${Math.round(x.battery)}%` : '';
  }

  _setMode(mode) {
    if (this.config.demo) return;
    const e = evFind(this._hass, this.config);
    if (e.mode) this._hass.callService('select', 'select_option', { entity_id: e.mode, option: mode });
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
